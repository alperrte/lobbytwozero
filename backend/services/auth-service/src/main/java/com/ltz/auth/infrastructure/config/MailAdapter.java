package com.ltz.auth.infrastructure.config;

/**
 * {@code ltz.auth.mail.adapter} değerleri. Production yalnız {@code productionReady} adaptörle başlar;
 * gerçek Mail Service adaptörü eklenene kadar böyle bir adaptör yoktur (PENDING).
 */
public enum MailAdapter {

    /** Teslimi açıkça reddeder; kod DELIVERY_FAILED olur. */
    UNAVAILABLE(false),
    /** Yalnız dev profili: Mailpit'e SMTP ile iletir. Mailpit dışarıya e-posta göndermez. */
    DEV_SMTP(false);

    private final boolean productionReady;

    MailAdapter(boolean productionReady) {
        this.productionReady = productionReady;
    }

    public boolean isProductionReady() {
        return productionReady;
    }
}
