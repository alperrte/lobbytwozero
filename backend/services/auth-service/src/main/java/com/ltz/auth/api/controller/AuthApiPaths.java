package com.ltz.auth.api.controller;

/** Gateway formatındaki yollar (/api/v1/auth/**); güvenlik yapılandırması da bu sabitleri kullanır. */
public final class AuthApiPaths {

    public static final String BASE = "/api/v1/auth";
    public static final String REGISTER = BASE + "/register";
    public static final String EMAIL_VERIFICATION = BASE + "/email-verification";
    public static final String EMAIL_VERIFICATION_CONFIRM = EMAIL_VERIFICATION + "/confirm";
    public static final String EMAIL_VERIFICATION_RESEND = EMAIL_VERIFICATION + "/resend";

    public static final String LOGIN = BASE + "/login";
    public static final String LOGIN_TWO_FACTOR = LOGIN + "/2fa";
    public static final String CSRF = BASE + "/csrf";
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

    public static final String PASSWORD = BASE + "/password";
    public static final String PASSWORD_CHANGE = PASSWORD + "/change";
    public static final String PASSWORD_FORGOT = PASSWORD + "/forgot";
    public static final String PASSWORD_RESET = PASSWORD + "/reset";

    public static final String ACCOUNT_DELETION = BASE + "/account/deletion";
    public static final String ACCOUNT_DELETION_REQUEST = ACCOUNT_DELETION + "/request";
    public static final String ACCOUNT_DELETION_CONFIRM = ACCOUNT_DELETION + "/confirm";

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
