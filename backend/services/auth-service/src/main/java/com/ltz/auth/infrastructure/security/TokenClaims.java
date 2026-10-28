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

    /** D4 servis token'ı türü; kullanıcı token'ı ({@link #ACCESS_TOKEN_TYPE}) yerine geçemez, tersi de geçemez. */
    public static final String SERVICE_TOKEN_TYPE = "svc+jwt";
    /** Servis token'ında {@code sub} öneki: {@code svc:<çağıran servis>}. */
    public static final String SERVICE_SUBJECT_PREFIX = "svc:";
    /** Auth'un servis kimliği; hesap verisi silme yetkisi yalnız bu kimliktedir. */
    public static final String AUTH_SERVICE_SUBJECT = SERVICE_SUBJECT_PREFIX + "auth-service";
    /** RFC 8693 kapsam claim'i (boşlukla ayrılmış). */
    public static final String SCOPE = "scope";
    /** Hesap verisi silme kapsamı. */
    public static final String ACCOUNT_ERASE_SCOPE = "account:erase";
    /** Servis token'ının bağlı olduğu tek hesap; hedef servis bunu istek yolundaki hesap kimliğiyle karşılaştırır. */
    public static final String ACCOUNT_ID = "account_id";
    /** Auth'a gelen iç çağrılarda (servis → Auth) {@code aud}: yalnız Auth'un kendi adı. */
    public static final String AUTH_SERVICE_AUDIENCE = "auth-service";
    /** Servis → Auth kapsamları (iç yönetim uçları, {@code docs-auth/servis-sozlesmeleri.md}). */
    public static final String ACCOUNT_READ_SCOPE = "account:read";
    public static final String ACCOUNT_STATUS_WRITE_SCOPE = "account:status:write";
    public static final String ACCOUNT_ROLE_WRITE_SCOPE = "account:role:write";
    /** Servisler arası olay girişi: hedef servisin {@code POST /internal/events} ucu; Auth yalnız izinli (hedef, kapsam) çiftine token verir. */
    public static final String EVENTS_INGEST_SCOPE = "events:ingest";
    /** Masaüstü cihaz token'ı türü; kullanıcı ({@link #ACCESS_TOKEN_TYPE}) ve servis token'ının yerine geçemez, tersi de geçemez. */
    public static final String DEVICE_TOKEN_TYPE = "dev+jwt";
    /** Cihaz token'ının tek hedefi ve tek kapsamı (Hardware'in masaüstü senkronu). */
    public static final String HARDWARE_SERVICE_AUDIENCE = "hardware-service";
    public static final String HARDWARE_SYNC_WRITE_SCOPE = "hardware:sync:write";
    /** Servis adı → servis token'ı {@code aud} değeri: {@code <servis>-service} (Compose ve modül adıyla aynı). */
    public static final String SERVICE_AUDIENCE_SUFFIX = "-service";

    private TokenClaims() {
    }
}
