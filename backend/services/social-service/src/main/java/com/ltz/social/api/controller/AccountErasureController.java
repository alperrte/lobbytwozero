package com.ltz.social.api.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.social.application.service.AccountErasureService;
import com.ltz.social.infrastructure.security.TokenClaims;

import lombok.RequiredArgsConstructor;

/**
 * Auth → Social hesap verisi silme (iç uç, kullanıcıya açık değil). Yalnız {@code scope=account:erase} taşıyan, Auth'un
 * imzaladığı servis token'ıyla ({@code svc+jwt}) çağrılır; kullanıcı token'ı kabul edilmez. Çağıran tam olarak
 * {@code svc:auth-service} olmalı ve token'ın {@code account_id} claim'i yoldaki hesapla aynı olmalıdır; aksi halde
 * {@code 403} döner ve hiçbir şey silinmez. İdempotenttir: veri olmasa da {@code 204} döner.
 */
@RestController
@RequiredArgsConstructor
public class AccountErasureController {

    private final AccountErasureService erasure;

    @DeleteMapping(InternalApiPaths.ACCOUNT_ERASURE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void erase(@PathVariable UUID accountId, @AuthenticationPrincipal Jwt token) {
        // accountId UUID olarak çözülüp küçük harfli yazılır; Auth claim'i de küçük harf yollar.
        if (!TokenClaims.AUTH_SERVICE_SUBJECT.equals(token.getSubject())
                || !accountId.toString().equals(token.getClaimAsString(TokenClaims.ACCOUNT_ID))) {
            throw new AccessDeniedException("Service token is not valid for this erasure");
        }
        erasure.erase(accountId);
    }
}
