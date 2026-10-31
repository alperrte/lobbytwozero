package com.ltz.mail.domain.enums;

/** E-posta türü; adı aynı zamanda {@code mail_templates.code} değeridir. */
public enum MailType {
    /** Kayıt ve e-posta değişikliği doğrulama kodu (MAIL-01). */
    EMAIL_VERIFICATION,
    /** Parola sıfırlama bağlantısı (MAIL-02). */
    PASSWORD_RESET_LINK,
    /** Hesap silme onay bağlantısı (MAIL-03). */
    ACCOUNT_DELETION_CONFIRMATION,
    PASSWORD_CHANGED,
    PASSWORD_RESET_COMPLETED,
    EMAIL_CHANGED,
    ACCOUNT_SUSPENDED,
    ACCOUNT_BANNED,
    ACCOUNT_DISABLED,
    ACCOUNT_REACTIVATED
}
