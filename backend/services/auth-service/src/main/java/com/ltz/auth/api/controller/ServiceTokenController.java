package com.ltz.auth.api.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.auth.api.dto.request.ServiceTokenRequest;
import com.ltz.auth.api.dto.response.ServiceTokenResponse;
import com.ltz.auth.application.service.ServiceClientTokenService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Servis → Auth token ucu (client credentials). Yalnız iç ağdan çağrılır; {@code /internal/**} Gateway'e açılmaz.
 * Kimlik yalnız gövdedeki gizli anahtardır (cookie okunmaz).
 */
@RestController
@RequiredArgsConstructor
public class ServiceTokenController {

    private final ServiceClientTokenService tokenService;

    @PostMapping(AuthApiPaths.SERVICE_TOKENS)
    public ResponseEntity<ServiceTokenResponse> token(@Valid @RequestBody ServiceTokenRequest request) {
        ServiceTokenResponse body = ServiceTokenResponse.from(
                tokenService.issue(request.clientId(), request.clientSecret(), request.audience()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
