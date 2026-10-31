package com.ltz.auth.api.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.ltz.auth.api.dto.request.ExternalIdentityReauthRequest;
import com.ltz.auth.api.dto.response.ExternalLinkStartResponse;
import com.ltz.auth.application.service.ExternalIdentityLinkService;
import com.ltz.auth.application.service.StartedFlow;
import com.ltz.auth.domain.exception.AuthException;
import com.ltz.auth.infrastructure.config.OAuthProperties;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Oturumdaki hesaba Steam bağlama ve kaldırma. Başlatma ve kaldırma kullanıcı oturumuyla (çerez + CSRF) ve yeniden
 * doğrulamayla çalışır. Steam dönüşü tarayıcı gezinmesidir: sonuç, giriş akışındaki gibi yapılandırmadaki SABİT frontend
 * adresine URL fragment'i ile iletilir ({@code status=steam_linked} veya {@code error=KOD}); token taşınmaz.
 */
@RestController
@RequiredArgsConstructor
public class ExternalIdentityLinkController {

    private final ExternalIdentityLinkService linkService;
    private final SessionCookies sessionCookies;
    private final OAuthProperties properties;

    @PostMapping(AuthApiPaths.STEAM_LINK_START)
    public ResponseEntity<ExternalLinkStartResponse> steamLinkStart(@AuthenticationPrincipal Jwt jwt,
                                                                    @Valid @RequestBody ExternalIdentityReauthRequest request,
                                                                    HttpServletResponse response) {
        StartedFlow flow = linkService.startSteamLink(UUID.fromString(jwt.getSubject()), request.password(),
                request.code(), request.recoveryCode());
        sessionCookies.writeOAuthState(response, flow.state(), properties.flowTtl());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ExternalLinkStartResponse(flow.redirectUri().toString()));
    }

    /** Oturum çerezini okumaz; hesap, state çerezine bağlı tek kullanımlık akıştan gelir. */
    @GetMapping(AuthApiPaths.STEAM_LINK_CALLBACK)
    public ResponseEntity<Void> steamLinkCallback(HttpServletRequest request, HttpServletResponse response) {
        try {
            linkService.completeSteamLink(request.getParameterMap(), sessionCookies.oauthState(request).orElse(null));
            return frontend("status=steam_linked");
        } catch (AuthException exception) {
            return frontend("error=" + exception.errorCode().name());
        } finally {
            // State tek kullanımlıktır; sonuç ne olursa olsun çerez silinir.
            sessionCookies.clearOAuthState(response);
        }
    }

    @PostMapping(AuthApiPaths.STEAM_UNLINK)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void steamUnlink(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ExternalIdentityReauthRequest request) {
        linkService.unlinkSteam(UUID.fromString(jwt.getSubject()), request.password(), request.code(),
                request.recoveryCode());
    }

    private ResponseEntity<Void> frontend(String fragment) {
        URI location = UriComponentsBuilder.fromUri(properties.frontendRedirectUrl()).fragment(fragment).build().toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }
}
