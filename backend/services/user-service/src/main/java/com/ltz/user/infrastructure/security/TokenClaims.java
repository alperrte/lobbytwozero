package com.ltz.user.infrastructure.security;

import java.time.Duration;
import java.util.Set;

/** Auth Service'in token'larında bu servisin kullandığı alanlar. */
public final class TokenClaims {

    /** JWT başlığı {@code typ}: kullanıcı access token'ı. */
    public static final String ACCESS_TOKEN_TYPE = "at+jwt";
    /** Auth'un servisler arası (iç uç) token türü; kullanıcı token'ıyla karıştırılmaz (D4 sözleşmesi). */
    public static final String SERVICE_TOKEN_TYPE = "svc+jwt";
    /** Servis token'ının {@code sub} değeri {@code svc:<çağıran>} biçimindedir. */
    public static final String SERVICE_SUBJECT_PREFIX = "svc:";
    /** Servis token'ı en fazla bu kadar yaşar (D4 sözleşmesi: exp ≤ 60 sn). */
    public static final Duration SERVICE_TOKEN_MAX_LIFETIME = Duration.ofSeconds(60);
    /** Hesap verisi silme eylemi için servis token'ı kapsamı. */
    public static final String ACCOUNT_ERASE_SCOPE = "account:erase";
    /** Servisler arası olay girişi (POST /internal/events) için servis token'ı kapsamı. */
    public static final String EVENTS_INGEST_SCOPE = "events:ingest";
    /** Gizlilik kararı okuma (POST /internal/v1/privacy/decisions) için servis token'ı kapsamı (talep T3). */
    public static final String PRIVACY_READ_SCOPE = "privacy:read";
    /** Gizlilik kararını yalnız bu servisler isteyebilir (SOC-12, HW-08, LIB-11). */
    public static final Set<String> PRIVACY_READ_SUBJECTS =
            Set.of("svc:social-service", "svc:hardware-service", "svc:library-service");
    /** Hesap silmeyi yalnız Auth başlatır: silme token'ının {@code sub} değeri tam bu olmalıdır. */
    public static final String AUTH_SERVICE_SUBJECT = "svc:auth-service";
    /** Silme/olay token'ının bağlı olduğu hesap. */
    public static final String ACCOUNT_ID = "account_id";
    /** Rol listesi, öneksiz ad ({@code PLAYER}, {@code ADMIN} ...). */
    public static final String ROLES = "roles";

    private TokenClaims() {
    }
}
