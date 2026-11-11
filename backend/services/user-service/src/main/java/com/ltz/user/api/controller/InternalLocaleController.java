package com.ltz.user.api.controller;

import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.user.api.dto.response.LocaleResponse;
import com.ltz.user.application.service.PreferencesService;
import com.ltz.user.domain.exception.UserErrorCode;
import com.ltz.user.domain.exception.UserException;
import com.ltz.user.infrastructure.security.TokenClaims;

import lombok.RequiredArgsConstructor;

/**
 * Auth → User dil okuma (iç uç, kullanıcıya açık değil; MAIL-AUTH-REQ-02). Auth, kullanıcıya gidecek e-postanın dilini
 * seçmek için çağırır. Yalnız {@code scope=preferences:locale:read} taşıyan, Auth'un imzaladığı servis token'ıyla
 * ({@code svc+jwt}) çağrılır; çağıran tam olarak {@code svc:auth-service} olmalı ve token'ın {@code account_id} claim'i
 * yoldaki kullanıcıyla aynı olmalıdır, aksi halde {@code 403}. Yalnız dil döner (tema ve diğer tercihler dönmez);
 * profil ya da tercih satırı yoksa {@code 404} (Auth bunu "dil bilinmiyor" sayar). Dil kişisel veri sayıldığından yanıt ve
 * değer loglanmaz ve önbelleğe alınmaz.
 */
@RestController
@RequiredArgsConstructor
public class InternalLocaleController {

    private final PreferencesService preferences;

    @GetMapping(InternalApiPaths.USER_LOCALE)
    public ResponseEntity<LocaleResponse> locale(@PathVariable UUID userId, @AuthenticationPrincipal Jwt token) {
        // userId UUID olarak çözülüp küçük harfli yazılır; Auth claim'i de küçük harf yollar.
        if (!TokenClaims.AUTH_SERVICE_SUBJECT.equals(token.getSubject())
                || !userId.toString().equals(token.getClaimAsString(TokenClaims.ACCOUNT_ID))) {
            throw new AccessDeniedException("Service token is not valid for this locale read");
        }
        String locale = preferences.savedLocale(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.PROFILE_NOT_FOUND));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new LocaleResponse(locale));
    }
}
