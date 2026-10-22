package com.ltz.social.infrastructure.security;

/** Auth Service'in access token'ında bu servisin kullandığı alanlar. */
public final class TokenClaims {

    /** JWT başlığı {@code typ}: kullanıcı access token'ı (ileride gelecek {@code svc+jwt} ile karıştırılmaz). */
    public static final String ACCESS_TOKEN_TYPE = "at+jwt";
    /** Rol listesi, öneksiz ad ({@code PLAYER}, {@code ADMIN} ...). */
    public static final String ROLES = "roles";

    private TokenClaims() {
    }
}
