package com.ltz.mail.infrastructure.security;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Servis token'ı ile istek hesabının eşleşmesi. Çağıran tam olarak {@code svc:auth-service} olmalı ve token'ın
 * {@code account_id} claim'i istekteki hesapla aynı olmalıdır: çalınan bir token başka hesap için e-posta
 * tetikleyemez ve başka hesabın verisini sildiremez.
 */
public final class ServiceCallerGuard {

    private ServiceCallerGuard() {
    }

    /** @throws AccessDeniedException eşleşme yoksa ({@code 403}); hiçbir işlem yapılmaz */
    public static void requireAuthForAccount(Jwt token, UUID accountId) {
        // accountId UUID olarak çözülüp küçük harfli yazılır; Auth claim'i de küçük harf yollar.
        if (!TokenClaims.AUTH_SERVICE_SUBJECT.equals(token.getSubject())
                || !accountId.toString().equals(token.getClaimAsString(TokenClaims.ACCOUNT_ID))) {
            throw new AccessDeniedException("Service token is not valid for this account");
        }
    }
}
