package com.ltz.user.api.controller;

import java.util.Optional;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.user.api.dto.request.PrivacyDecisionRequest;
import com.ltz.user.api.dto.response.PrivacyDecisionResponse;
import com.ltz.user.application.service.PrivacyDecisionService;
import com.ltz.user.infrastructure.security.TokenClaims;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Servislerin gizlilik kararı (iç uç, kullanıcıya açık değil). Yalnız {@code scope=privacy:read} taşıyan ve çağıranı
 * izinli listede (social, hardware, library) olan servis token'ıyla çağrılır; aksi halde {@code 403}. Kullanıcı token'ı
 * {@code 401}. {@code viewerId} çağıran servisin kendi doğruladığı kullanıcıdır; bu uç onu yeniden doğrulayamaz, bu yüzden
 * yanıt yalnız güvenilen servislere verilir.
 */
@RestController
@RequiredArgsConstructor
public class PrivacyDecisionController {

    private final PrivacyDecisionService decisions;

    @PostMapping(InternalApiPaths.PRIVACY_DECISIONS)
    public PrivacyDecisionResponse decide(@Valid @RequestBody PrivacyDecisionRequest request,
                                          @AuthenticationPrincipal Jwt token) {
        if (!TokenClaims.PRIVACY_READ_SUBJECTS.contains(token.getSubject())) {
            throw new AccessDeniedException("Service is not allowed to read privacy decisions");
        }
        return new PrivacyDecisionResponse(
                decisions.decide(Optional.ofNullable(request.viewerId()), request.field(), request.ownerIds()));
    }
}
