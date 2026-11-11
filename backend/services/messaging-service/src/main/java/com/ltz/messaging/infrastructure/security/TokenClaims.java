package com.ltz.messaging.infrastructure.security;

import java.time.Duration;

/** Auth Service'in kullanıcı access token'ında ve servis token'ında bu servisin kullandığı alanlar. */
public final class TokenClaims {

    /** JWT başlığı {@code typ}: kullanıcı access token'ı (servis token'ı {@code svc+jwt} ile karıştırılmaz). */
    public static final String ACCESS_TOKEN_TYPE = "at+jwt";
    /** JWT başlığı {@code typ}: Auth'un servis token'ı (docs-auth/servis-sozlesmeleri.md §3). */
    public static final String SERVICE_TOKEN_TYPE = "svc+jwt";
    /** Servis token'ında {@code sub} öneki; çağıran servisin adı ardından gelir. */
    public static final String SERVICE_SUBJECT_PREFIX = "svc:";
    /** Hesap silme yetkisi yalnız Auth'tadır: silme ucunda {@code sub} tam olarak bu olmalıdır. */
    public static final String AUTH_SERVICE_SUBJECT = "svc:auth-service";
    /** Silme token'ının bağlandığı hesap; yoldaki hesapla aynı olmalıdır. */
    public static final String ACCOUNT_ID = "account_id";
    public static final String SCOPE = "scope";
    public static final String ACCOUNT_ERASE_SCOPE = "account:erase";
    /** Servis token'ının en uzun ömrü (Auth 30 sn verir). */
    public static final Duration SERVICE_TOKEN_MAX_LIFETIME = Duration.ofSeconds(60);
    /** Rol listesi, öneksiz ad ({@code PLAYER}, {@code ADMIN} ...). */
    public static final String ROLES = "roles";

    private TokenClaims() {
    }
}
