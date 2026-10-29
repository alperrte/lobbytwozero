package com.ltz.social.api.controller;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.social.api.dto.request.BlocksCheckRequest;
import com.ltz.social.api.dto.response.BlocksCheckResponse;
import com.ltz.social.application.service.BlockCheckService;
import com.ltz.social.infrastructure.security.TokenClaims;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * messaging-service ve lfp-service → Social engel sorgusu (iç uç, kullanıcıya açık değil). Yalnız Auth'un imzaladığı,
 * {@code scope=blocks:check} taşıyan servis token'ı kabul edilir; çağıran ({@code sub}) bu iki servisten biri olmalıdır.
 * Kapsam aynı olsa bile başka bir servis kimliği {@code 403} alır.
 */
@RestController
@RequiredArgsConstructor
public class BlocksCheckController {

    private final BlockCheckService blockCheck;

    @PostMapping(InternalApiPaths.BLOCKS_CHECK)
    public BlocksCheckResponse check(@Valid @RequestBody BlocksCheckRequest request, @AuthenticationPrincipal Jwt token) {
        if (!TokenClaims.BLOCKS_CHECK_SUBJECTS.contains(token.getSubject())) {
            throw new AccessDeniedException("Service token is not allowed to query blocks");
        }
        return BlocksCheckResponse.from(blockCheck.check(request.userId(), request.otherUserIds()));
    }
}
