package com.ltz.mail.infrastructure.security;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

import com.ltz.mail.infrastructure.config.PlatformProperties;

/**
 * Servis token'ı ile istek hesabının eşleşmesi. Çağıran tam olarak {@code svc:auth-service} olmalı ve token'ın
 * {@code account_id} claim'i istekteki hesapla aynı olmalıdır: çalınan bir token başka hesap için e-posta
 * tetikleyemez ve başka hesabın verisini sildiremez.
 */
public final class ServiceCallerGuard {

    private ServiceCallerGuard() {
    }

    /**
     * Platform kapısı: çağıran, izin tablosunda kayıtlı bir servis olmalıdır (Auth bu uçtan gönderemez). İstemci-kimlikli
     * token'da {@code account_id} claim'i yoktur; hesabı gövdedeki {@code accountId} belirler.
     *
     * @return çağıranın izin satırı
     * @throws AccessDeniedException kayıtsız çağıran ({@code 403})
     */
    public static PlatformProperties.Caller requirePlatformCaller(Jwt token, PlatformProperties platform) {
        PlatformProperties.Caller caller = platform.callerFor(token.getSubject());
        if (caller == null) {
            throw new AccessDeniedException("Service is not allowed to send notices");
        }
        return caller;
    }

    /** Token'daki çağıran adı ({@code svc:} öneki atılmış); {@link #requirePlatformCaller} sonrasında kullanılır. */
    public static String callerName(Jwt token) {
        return token.getSubject().substring(TokenClaims.SERVICE_SUBJECT_PREFIX.length());
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
