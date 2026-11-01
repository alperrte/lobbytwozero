package com.ltz.messaging.api.controller;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

/** {@code userId} yalnız doğrulanmış token'ın {@code sub} alanından alınır; istek gövdesi/başlığındaki kimlik güvenilmez. */
final class AuthenticatedUser {

    private AuthenticatedUser() {
    }

    static UUID id(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            // Auth her zaman UUID üretir; farklı biçim sahte/hatalı token demektir.
            throw new InvalidBearerTokenException("Invalid subject", exception);
        }
    }
}
