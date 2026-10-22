package com.ltz.auth.infrastructure.security;

/** Access token sözleşmesindeki özel claim'ler (diğer servisler de aynı adları kullanır). */
public final class TokenClaims {

    /** RFC 9068: OAuth 2.0 access token JWT türü; kimlik token'ı vb. ile karıştırılmayı engeller. */
    public static final String ACCESS_TOKEN_TYPE = "at+jwt";
    /** Oturum (refresh ailesi) kimliği. */
    public static final String SESSION_ID = "sid";
    /** Hesabın token sürümü; tüm oturumlardan çıkışta artar. */
    public static final String TOKEN_VERSION = "ver";
    public static final String ROLES = "roles";
    /** Kimlik doğrulama yöntemleri (RFC 8176). */
    public static final String AUTH_METHODS = "amr";

    private TokenClaims() {
    }
}
