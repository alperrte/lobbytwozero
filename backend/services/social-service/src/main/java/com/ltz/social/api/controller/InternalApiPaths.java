package com.ltz.social.api.controller;

/**
 * Servisler arası (iç) uçlar. Gateway yalnız {@code /api/v1/<servis>/**} yollarını route eder; bu yollar dışarıya
 * açılmaz ve yalnız Auth'un imzaladığı servis token'ıyla ({@code svc+jwt}) çağrılabilir.
 */
public final class InternalApiPaths {

    public static final String BASE = "/internal";
    public static final String ACCOUNT_ERASURE = BASE + "/v1/accounts/{accountId}";
    public static final String BLOCKS_CHECK = BASE + "/v1/blocks/check";

    private InternalApiPaths() {
    }
}
