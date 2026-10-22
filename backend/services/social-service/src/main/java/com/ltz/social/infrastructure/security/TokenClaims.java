package com.ltz.social.infrastructure.security;

import java.time.Duration;

/** Auth Service'in access token'ında bu servisin kullandığı alanlar. */
public final class TokenClaims {

    /** JWT başlığı {@code typ}: kullanıcı access token'ı (ileride gelecek {@code svc+jwt} ile karıştırılmaz). */
    public static final String ACCESS_TOKEN_TYPE = "at+jwt";
    /** Auth'un servisler arası (iç uç) token türü; kullanıcı token'ıyla karıştırılmaz (D4 sözleşmesi). */
    public static final String SERVICE_TOKEN_TYPE = "svc+jwt";
    /** Servis token'ının {@code sub} değeri {@code svc:<çağıran>} biçimindedir. */
    public static final String SERVICE_SUBJECT_PREFIX = "svc:";
    /** Servis token'ı en fazla bu kadar yaşar (D4 sözleşmesi: exp ≤ 60 sn). */
    public static final Duration SERVICE_TOKEN_MAX_LIFETIME = Duration.ofSeconds(60);
    /** Hesap verisi silme eylemi için servis token'ı kapsamı. */
    public static final String ACCOUNT_ERASE_SCOPE = "account:erase";
    /** Rol listesi, öneksiz ad ({@code PLAYER}, {@code ADMIN} ...). */
    public static final String ROLES = "roles";

    private TokenClaims() {
    }
}
