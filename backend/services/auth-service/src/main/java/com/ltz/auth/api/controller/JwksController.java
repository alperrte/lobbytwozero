package com.ltz.auth.api.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.auth.infrastructure.security.JwtSigningKey;

import lombok.RequiredArgsConstructor;

/** Diğer servislerin access token imzasını doğrulaması için açık anahtar kümesi (RFC 7517). */
@RestController
@RequiredArgsConstructor
public class JwksController {

    private final JwtSigningKey signingKey;

    @GetMapping(AuthApiPaths.JWKS)
    public Map<String, Object> jwks() {
        return signingKey.publicJwkSet();
    }
}
