package com.ltz.mail.api.controller;

/**
 * Servisler arası (iç) uçlar. Gateway yalnız {@code /api/v1/<servis>/**} yollarını route eder; bu yollar dışarıya
 * açılmaz ve yalnız Auth'un imzaladığı servis token'ıyla ({@code svc+jwt}) çağrılabilir. Mail Service'in kullanıcıya
 * açık hiçbir ucu yoktur.
 */
public final class InternalApiPaths {

    public static final String BASE = "/internal";
    public static final String MAIL = BASE + "/v1/mail";
    public static final String VERIFICATION_CODES = MAIL + "/verification-codes";
    public static final String PASSWORD_RESETS = MAIL + "/password-resets";
    public static final String ACCOUNT_DELETIONS = MAIL + "/account-deletions";
    public static final String SECURITY_NOTIFICATIONS = MAIL + "/security-notifications";
    public static final String ACCOUNT_ERASURE = BASE + "/v1/accounts/{accountId}";

    private InternalApiPaths() {
    }
}
