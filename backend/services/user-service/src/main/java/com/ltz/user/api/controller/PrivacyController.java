package com.ltz.user.api.controller;

import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.user.api.dto.request.UpdatePrivacyRequest;
import com.ltz.user.api.dto.response.PrivacySettingsResponse;
import com.ltz.user.application.service.PrivacyService;
import com.ltz.user.application.service.UpdatePrivacyCommand;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Kendi gizlilik ayarlarım (USER-06). Kimlik yalnız token'dan; yanıtlar önbelleğe alınmaz. */
@RestController
@RequiredArgsConstructor
public class PrivacyController {

    private final PrivacyService privacy;

    @GetMapping(UserApiPaths.ME_PRIVACY)
    public ResponseEntity<PrivacySettingsResponse> get(@AuthenticationPrincipal Jwt token) {
        return noStore(PrivacySettingsResponse.from(privacy.get(userId(token))));
    }

    @PutMapping(UserApiPaths.ME_PRIVACY)
    public ResponseEntity<PrivacySettingsResponse> update(@AuthenticationPrincipal Jwt token,
                                                          @Valid @RequestBody UpdatePrivacyRequest request) {
        UpdatePrivacyCommand command = new UpdatePrivacyCommand(request.version(), request.friendsList(),
                request.library(), request.hardware(), request.performance(), request.guestProfileAccess());
        return noStore(PrivacySettingsResponse.from(privacy.update(userId(token), command)));
    }

    private static UUID userId(Jwt token) {
        return UUID.fromString(token.getSubject());
    }

    private static ResponseEntity<PrivacySettingsResponse> noStore(PrivacySettingsResponse body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
