package com.ltz.social.api.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.social.application.service.AccountErasureService;

import lombok.RequiredArgsConstructor;

/**
 * Auth → Social hesap verisi silme (iç uç, kullanıcıya açık değil). Yalnız {@code scope=account:erase} taşıyan, Auth'un
 * imzaladığı servis token'ıyla ({@code svc+jwt}) çağrılır; kullanıcı token'ı kabul edilmez. İdempotenttir: veri
 * olmasa da {@code 204} döner.
 */
@RestController
@RequiredArgsConstructor
public class AccountErasureController {

    private final AccountErasureService erasure;

    @DeleteMapping(InternalApiPaths.ACCOUNT_ERASURE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void erase(@PathVariable UUID accountId) {
        erasure.erase(accountId);
    }
}
