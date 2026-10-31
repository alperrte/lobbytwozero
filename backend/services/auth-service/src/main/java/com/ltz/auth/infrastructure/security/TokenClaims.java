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
    /** Auth'a gelen iç çağrılarda (servis → Auth) {@code aud}: yalnız Auth'un kendi adı. */
    public static final String AUTH_SERVICE_AUDIENCE = "auth-service";
    /** Auth'un servis kimliği; hesap verisi silme yetkisi yalnız bu kimliktedir. */
    public static final String AUTH_SERVICE_SUBJECT = SERVICE_SUBJECT_PREFIX + AUTH_SERVICE_AUDIENCE;
    /** RFC 8693 kapsam claim'i (boşlukla ayrılmış). */
    public static final String SCOPE = "scope";
    /** Mail Service'e e-posta kuyruğa alma kapsamı. */
    public static final String MAIL_SEND_SCOPE = "mail:send";
    /** Hesap verisi silme kapsamı. */
    public static final String ACCOUNT_ERASE_SCOPE = "account:erase";
    /** Servis token'ının bağlı olduğu tek hesap; hedef servis bunu istek yolundaki hesap kimliğiyle karşılaştırır. */
    public static final String ACCOUNT_ID = "account_id";
    /** Servis → Auth kapsamları (iç yönetim uçları, {@code docs-auth/servis-sozlesmeleri.md}). */
    public static final String ACCOUNT_READ_SCOPE = "account:read";
    public static final String ACCOUNT_STATUS_WRITE_SCOPE = "account:status:write";
    public static final String ACCOUNT_ROLE_WRITE_SCOPE = "account:role:write";
    /** Servisler arası olay girişi: hedef servisin {@code POST /internal/events} ucu; Auth yalnız izinli (hedef, kapsam) çiftine token verir. */
    public static final String EVENTS_INGEST_SCOPE = "events:ingest";
    /** Engel sorgusu: messaging ve lfp, Social'ın {@code POST /internal/v1/blocks/check} ucunu çağırır. */
    public static final String BLOCKS_CHECK_SCOPE = "blocks:check";
    /**
     * Gizlilik kararı: social, hardware ve library, User'ın {@code POST /internal/v1/privacy/decisions} ucundan başkasının
     * verisini açmadan önce izin/ret kararı ister. Kullanıcıya bağlı değildir; görüntüleyen ve sahip istek gövdesindedir.
     */
    public static final String PRIVACY_READ_SCOPE = "privacy:read";
    /**
     * Öneri sinyali okuma: recommendation, Library'den tek hesabın sinyallerini okur. Hesaba bağlı kapsamdır: token
     * {@link #ACCOUNT_ID} taşır, hedef servis bunu istekteki hesapla karşılaştırır.
     */
    public static final String SIGNALS_READ_SCOPE = "signals:read";
    /** Oyun puan özeti okuma: game, Review'ın toplu puan özetini yalnız okur; kullanıcıya bağlı değildir. */
    public static final String RATING_SUMMARIES_READ_SCOPE = "rating-summaries:read";
    /**
     * Cihaz doğrulama: hardware, Performance'tan bir hesabın cihazının aktif olup olmadığını sorar. Hesaba bağlı kapsamdır:
     * token {@link #ACCOUNT_ID} taşır, Performance bunu istek yolundaki hesapla karşılaştırır.
     */
    public static final String DEVICES_VERIFY_SCOPE = "devices:verify";
    /** Masaüstü cihaz token'ı türü; kullanıcı ({@link #ACCESS_TOKEN_TYPE}) ve servis token'ının yerine geçemez, tersi de geçemez. */
    public static final String DEVICE_TOKEN_TYPE = "dev+jwt";
    /** Cihaz token'ının tek hedefi ve tek kapsamı (Hardware'in masaüstü senkronu). */
    public static final String HARDWARE_SERVICE_AUDIENCE = "hardware-service";
    public static final String HARDWARE_SYNC_WRITE_SCOPE = "hardware:sync:write";
    /** Cihaz token'ının ikinci hedefi ve kapsamı (Performance'ın ölçüm senkronu); token yine tek {@code aud}, tek kapsam taşır. */
    public static final String PERFORMANCE_SERVICE_AUDIENCE = "performance-service";
    public static final String PERFORMANCE_SYNC_WRITE_SCOPE = "performance:sync:write";
    /** Servis adı → servis token'ı {@code aud} değeri: {@code <servis>-service} (Compose ve modül adıyla aynı). */
    public static final String SERVICE_AUDIENCE_SUFFIX = "-service";

    private TokenClaims() {
    }
}
