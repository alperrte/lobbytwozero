package com.ltz.mail.application.port;

/**
 * Gönderim hatası. {@code permanent} ise tekrar denemenin anlamı yoktur (ör. alıcı reddedildi, şablon hatası);
 * değilse geçici sayılır ve geri çekilmeyle yeniden denenir. Hata kodu sabit bir metindir; alıcı adresi, SMTP sunucu
 * mesajı veya içerik taşımaz.
 */
public class MailDeliveryException extends RuntimeException {

    private final boolean permanent;
    private final String errorCode;
    private final Integer smtpCode;

    private MailDeliveryException(boolean permanent, String errorCode, Integer smtpCode) {
        super(errorCode);
        this.permanent = permanent;
        this.errorCode = errorCode;
        this.smtpCode = smtpCode;
    }

    public static MailDeliveryException permanent(String errorCode, Integer smtpCode) {
        return new MailDeliveryException(true, errorCode, smtpCode);
    }

    public static MailDeliveryException transientFailure(String errorCode, Integer smtpCode) {
        return new MailDeliveryException(false, errorCode, smtpCode);
    }

    public boolean isPermanent() {
        return permanent;
    }

    public String errorCode() {
        return errorCode;
    }

    public Integer smtpCode() {
        return smtpCode;
    }
}
