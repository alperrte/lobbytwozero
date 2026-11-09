package com.ltz.auth.api.controller;

/** Gateway formatındaki yollar (/api/v1/auth/**); güvenlik yapılandırması da bu sabitleri kullanır. */
public final class AuthApiPaths {

    private static final String CONFIRM = "/confirm";

    public static final String BASE = "/api/v1/auth";
    public static final String REGISTER = BASE + "/register";
    public static final String EMAIL_VERIFICATION = BASE + "/email-verification";
    public static final String EMAIL_VERIFICATION_CONFIRM = EMAIL_VERIFICATION + CONFIRM;
    public static final String EMAIL_VERIFICATION_RESEND = EMAIL_VERIFICATION + "/resend";

    public static final String LOGIN = BASE + "/login";
    public static final String LOGIN_TWO_FACTOR = LOGIN + "/2fa";
    /** Zorunlu 2FA'nın (Steam hesapları) ilk kurulumu: oturum yok, yalnız girişte verilen kurulum challenge'ı. */
    public static final String LOGIN_TWO_FACTOR_ENROLLMENT_START = LOGIN_TWO_FACTOR + "/enrollment/start";
    public static final String LOGIN_TWO_FACTOR_ENROLLMENT_COMPLETE = LOGIN_TWO_FACTOR + "/enrollment/complete";
    public static final String CSRF = BASE + "/csrf";
    public static final String ME = BASE + "/me";
    public static final String JWKS = BASE + "/.well-known/jwks.json";

    /** Refresh cookie'si yalnız bu yolun altına gönderilir. */
    public static final String SESSION = BASE + "/session";
    public static final String SESSION_REFRESH = SESSION + "/refresh";
    public static final String SESSION_LOGOUT = SESSION + "/logout";
    public static final String SESSION_LOGOUT_ALL = SESSION + "/logout-all";

    public static final String TOTP = BASE + "/2fa/totp";
    public static final String TOTP_SETUP = TOTP + "/setup";
    public static final String TOTP_ACTIVATE = TOTP + "/activate";
    public static final String TOTP_DISABLE = TOTP + "/disable";

    public static final String OAUTH = BASE + "/oauth";
    public static final String GOOGLE_START = OAUTH + "/google/start";
    public static final String GOOGLE_CALLBACK = OAUTH + "/google/callback";
    public static final String STEAM_START = OAUTH + "/steam/start";
    public static final String STEAM_CALLBACK = OAUTH + "/steam/callback";
    public static final String STEAM_COMPLETE = OAUTH + "/steam/complete";
    public static final String GOOGLE_COMPLETE = OAUTH + "/google/complete";
    /** Oturumdaki hesaba Steam bağlama (yeniden doğrulamalı başlatma, Steam dönüşü). Kullanıcı tarafı kaldırma yoktur. */
    public static final String STEAM_LINK_START = OAUTH + "/steam/link/start";
    public static final String STEAM_LINK_CALLBACK = OAUTH + "/steam/link/callback";
    /** Oturumdaki hesaba Google bağlama (yeniden doğrulamalı başlatma, Google dönüşü). */
    public static final String GOOGLE_LINK_START = OAUTH + "/google/link/start";
    public static final String GOOGLE_LINK_CALLBACK = OAUTH + "/google/link/callback";

    public static final String PASSWORD = BASE + "/password";
    public static final String PASSWORD_CHANGE = PASSWORD + "/change";
    /** Parolasız (Google/Steam) hesabın ilk parolasını belirlemesi: önce e-posta kodu istenir, sonra parola yazılır. */
    public static final String PASSWORD_SET_REQUEST = PASSWORD + "/set/request";
    public static final String PASSWORD_SET = PASSWORD + "/set";
    public static final String PASSWORD_FORGOT = PASSWORD + "/forgot";
    public static final String PASSWORD_RESET = PASSWORD + "/reset";

    public static final String EMAIL_CHANGE = BASE + "/email/change";
    public static final String EMAIL_CHANGE_CONFIRM = EMAIL_CHANGE + CONFIRM;
    public static final String ACCOUNT_DELETION = BASE + "/account/deletion";
    public static final String ACCOUNT_DELETION_REQUEST = ACCOUNT_DELETION + "/request";
    public static final String ACCOUNT_DELETION_CONFIRM = ACCOUNT_DELETION + CONFIRM;
    public static final String ACCOUNT_DELETION_CANCEL = ACCOUNT_DELETION + "/cancel";

    /** Masaüstü cihaz kimliği: bağlama, listeleme, iptal (oturumlu) ve cihaz token'ı (cihaz anahtarıyla, çerezsiz). */
    public static final String DEVICES = BASE + "/devices";
    public static final String DEVICE = DEVICES + "/{deviceId}";
    public static final String DEVICES_TOKEN = DEVICES + "/token";

    /** Servis → Auth iç uçları: Gateway'e açılmaz (Gateway yalnız /api/v1/<servis>/** yönlendirir). */
    public static final String INTERNAL = "/internal/v1";
    public static final String SERVICE_TOKENS = INTERNAL + "/service-tokens";
    public static final String INTERNAL_ACCOUNT = INTERNAL + "/accounts/{accountId}";
    public static final String INTERNAL_ACCOUNT_CONTACT = INTERNAL_ACCOUNT + "/contact";
    public static final String INTERNAL_ACCOUNT_STATUS =INTERNAL_ACCOUNT + "/status";
    public static final String INTERNAL_ACCOUNT_ROLES_GRANT = INTERNAL_ACCOUNT + "/roles/grant";
    public static final String INTERNAL_ACCOUNT_ROLES_REVOKE = INTERNAL_ACCOUNT + "/roles/revoke";
    /** Yalnız ROLE_ADMIN erişir (SecurityConfig). */
    public static final String ADMIN = BASE + "/admin";
    public static final String ADMIN_ROLES = ADMIN + "/roles";
    public static final String ADMIN_DELETIONS_PENDING = ADMIN + "/account-deletions/pending";
    public static final String ADMIN_ACCOUNT = ADMIN + "/accounts/{accountId}";
    public static final String ADMIN_ACCOUNT_STATUS = ADMIN_ACCOUNT + "/status";
    public static final String ADMIN_ACCOUNT_ROLES_GRANT = ADMIN_ACCOUNT + "/roles/grant";
    public static final String ADMIN_ACCOUNT_ROLES_REVOKE = ADMIN_ACCOUNT + "/roles/revoke";

    private AuthApiPaths() {
    }
}
