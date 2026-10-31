package com.ltz.auth.infrastructure.config;

/**
 * {@code ltz.auth.mail.adapter} değerleri. Production yalnız {@code productionReady} adaptörle başlar
 * ({@link #MAIL_SERVICE}).
 */
public enum MailAdapter {

    /** Teslimi açıkça reddeder; kod DELIVERY_FAILED olur. */
    UNAVAILABLE(false),
    /** Yalnız dev profili: Mailpit'e SMTP ile iletir. Mailpit dışarıya e-posta göndermez. */
    DEV_SMTP(false),
    /** Gerçek Mail Service: svc+jwt ile {@code /internal/v1/mail/**} uçlarına kuyruğa alma isteği gönderir. */
    MAIL_SERVICE(true);

    private final boolean productionReady;

    MailAdapter(boolean productionReady) {
        this.productionReady = productionReady;
    }

    public boolean isProductionReady() {
        return productionReady;
    }
}
