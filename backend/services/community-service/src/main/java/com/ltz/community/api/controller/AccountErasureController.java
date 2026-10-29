package com.ltz.community.api.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.community.application.service.AccountErasureService;
import com.ltz.community.infrastructure.security.TokenClaims;

/**
 * Auth → community hesap verisi silme (iç uç, kullanıcıya açık değil). Yalnız {@code scope=account:erase} taşıyan, Auth'un
 * imzaladığı servis token'ıyla ({@code svc+jwt}) çağrılır; kullanıcı token'ı kabul edilmez. Token'ın çağıranı tam olarak
 * {@code svc:auth-service} olmalı ve {@code account_id} claim'i yoldaki hesapla aynı olmalıdır; böylece çalınan bir token
 * başka hesabı silemez. İşlem idempotenttir: veri olmasa da {@code 204} döner.
 */
@RestController
public class AccountErasureController {

	private final AccountErasureService erasureService;

	public AccountErasureController(AccountErasureService erasureService) {
		this.erasureService = erasureService;
	}

	@DeleteMapping(InternalApiPaths.ACCOUNT_ERASURE)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void erase(@PathVariable UUID accountId, @AuthenticationPrincipal Jwt token) {
		boolean fromAuth = TokenClaims.AUTH_SERVICE_SUBJECT.equals(token.getSubject());
		boolean boundToThisAccount = accountId.toString().equals(token.getClaimAsString(TokenClaims.ACCOUNT_ID));
		if (!fromAuth || !boundToThisAccount) {
			throw new AccessDeniedException("Service token is not valid for this erasure");
		}
		erasureService.erase(accountId);
	}
}
