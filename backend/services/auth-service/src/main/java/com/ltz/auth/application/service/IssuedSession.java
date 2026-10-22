package com.ltz.auth.application.service;

import java.time.Instant;

/** Yeni açılan veya yenilenen oturumun token'ları. Yalnız HttpOnly cookie'ye yazılır, gövdede dönmez. */
public record IssuedSession(String accessToken, Instant accessTokenExpiresAt,
                            String refreshToken, Instant refreshTokenExpiresAt) {

    @Override
    public String toString() {
        return "IssuedSession[accessToken=***, accessTokenExpiresAt=" + accessTokenExpiresAt
                + ", refreshToken=***, refreshTokenExpiresAt=" + refreshTokenExpiresAt + "]";
    }
}
