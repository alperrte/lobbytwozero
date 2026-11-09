package com.ltz.mail.domain.enums;

/** E-posta türü; adı aynı zamanda şablon dosyasının adıdır ({@code mail/templates/<TÜR>.<dil>.mail}). */
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
    ACCOUNT_REACTIVATED,
    TWO_FACTOR_ENABLED,
    TWO_FACTOR_DISABLED,
    RECOVERY_CODE_USED,
    EXTERNAL_IDENTITY_LINKED,
    /** Hoş geldin e-postası; Auth e-posta doğrulaması tamamlanınca gönderir (MAIL-05). */
    WELCOME,
    /**
     * Genel bilgilendirme (platform kapısı): içeriği (başlık, mesaj, düğme) çağıran servis verir, tasarım Mail'indir.
     * Şablon dosyası yoktur; {@code TemplateRenderer} bileşenlerden kurar.
     */
    NOTICE;

    /** İçeriği dosyadan değil istekten gelen tür: katalogda şablon dosyası aranmaz. */
    public boolean isDynamic() {
        return this == NOTICE;
    }
}
