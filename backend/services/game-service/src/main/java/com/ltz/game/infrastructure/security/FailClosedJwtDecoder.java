package com.ltz.game.infrastructure.security;

import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/** İmza kaynağı yapılandırılmamışsa her token reddedilir; korumalı uçlar 401 verir. */
final class FailClosedJwtDecoder implements JwtDecoder {
    @Override
    public Jwt decode(String token) throws BadJwtException {
        throw new BadJwtException("JWT doğrulaması yapılandırılmamış.");
    }
}
