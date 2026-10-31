package com.ltz.mail.api.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.mail.application.service.AccountErasureService;
import com.ltz.mail.infrastructure.security.ServiceCallerGuard;

import lombok.RequiredArgsConstructor;

/**
 * Auth → Mail hesap verisi silme (iç uç, kullanıcıya açık değil). Yalnız {@code scope=account:erase} taşıyan, Auth'un
 * imzaladığı servis token'ıyla çağrılır. Çağıran tam olarak {@code svc:auth-service} olmalı ve token'ın
 * {@code account_id} claim'i yoldaki hesapla aynı olmalıdır; aksi halde {@code 403} döner ve hiçbir şey silinmez.
 * İdempotenttir: veri olmasa da {@code 204} döner.
 */
@RestController
@RequiredArgsConstructor
public class AccountErasureController {

    private final AccountErasureService erasure;

    @DeleteMapping(InternalApiPaths.ACCOUNT_ERASURE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void erase(@PathVariable UUID accountId, @AuthenticationPrincipal Jwt token) {
        ServiceCallerGuard.requireAuthForAccount(token, accountId);
        erasure.erase(accountId);
    }
}
