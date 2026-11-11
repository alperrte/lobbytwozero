package com.ltz.messaging.api.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.messaging.application.service.AccountErasureService;
import com.ltz.messaging.infrastructure.security.TokenClaims;

import lombok.RequiredArgsConstructor;

/**
 * Auth → Messaging hesap verisi silme (iç uç, kullanıcıya ve Gateway'e açık değil). Yalnız {@code scope=account:erase}
 * taşıyan Auth servis token'ıyla ({@code svc+jwt}) çağrılır. Çağıran tam olarak {@code svc:auth-service} olmalı ve
 * {@code account_id} yoldaki hesapla aynı olmalıdır; böylece başka servisin ya da çalınan bir token başka hesabı
 * silemez. İdempotenttir: veri olmasa da {@code 204}.
 */
@RestController
@RequiredArgsConstructor
public class AccountErasureController {

    private final AccountErasureService erasure;

    @DeleteMapping(InternalApiPaths.ACCOUNT_ERASURE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void erase(@PathVariable UUID accountId, @AuthenticationPrincipal Jwt token) {
        boolean fromAuth = TokenClaims.AUTH_SERVICE_SUBJECT.equals(token.getSubject());
        boolean boundToThisAccount = accountId.toString().equals(token.getClaimAsString(TokenClaims.ACCOUNT_ID));
        if (!fromAuth || !boundToThisAccount) {
            throw new AccessDeniedException("Service token is not valid for this erasure");
        }
        erasure.erase(accountId);
    }
}
