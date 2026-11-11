package com.ltz.messaging.api.controller;

/** Servisler arası iç uçlar; Gateway'e açılmaz, yalnız servis token'ıyla çağrılır. */
public final class InternalApiPaths {

    public static final String BASE = "/internal/v1";
    /** Auth → Messaging hesap verisi silme (docs-auth/d4-ekip-notlari/01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md). */
    public static final String ACCOUNT_ERASURE = BASE + "/accounts/{accountId}";

    private InternalApiPaths() {
    }
}
