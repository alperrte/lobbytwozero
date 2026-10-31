package com.ltz.mail.infrastructure.security;

import java.time.Duration;

/** Auth Service'in servis token'larında ({@code svc+jwt}) bu servisin kullandığı alanlar. */
public final class TokenClaims {

    /** Auth'un servisler arası (iç uç) token türü (D4 sözleşmesi). Kullanıcı token'ı ({@code at+jwt}) hiçbir uçta kabul edilmez. */
    public static final String SERVICE_TOKEN_TYPE = "svc+jwt";
    /** Servis token'ının {@code sub} değeri {@code svc:<çağıran>} biçimindedir. */
    public static final String SERVICE_SUBJECT_PREFIX = "svc:";
    /** Mail Service'e yalnız Auth istek atar: token'ın {@code sub} değeri tam bu olmalıdır. */
    public static final String AUTH_SERVICE_SUBJECT = SERVICE_SUBJECT_PREFIX + "auth-service";
    /** Servis token'ı en fazla bu kadar yaşar (D4 sözleşmesi: exp ≤ 60 sn). */
    public static final Duration SERVICE_TOKEN_MAX_LIFETIME = Duration.ofSeconds(60);
    /** E-posta gönderim talebi kapsamı. */
    public static final String MAIL_SEND_SCOPE = "mail:send";
    /** Hesap verisi silme kapsamı. */
    public static final String ACCOUNT_ERASE_SCOPE = "account:erase";
    /** Token'ın bağlı olduğu tek hesap; istek gövdesi/yolu ile karşılaştırılır. */
    public static final String ACCOUNT_ID = "account_id";
    public static final String SCOPE = "scope";

    private TokenClaims() {
    }
}
