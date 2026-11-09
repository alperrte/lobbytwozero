package com.ltz.auth.application.port;

/** Hesap sahibine giden güvenlik ve hesap durumu bildirimleri (MAIL-03, MAIL-04). */
public enum SecurityNotificationType {
    /** Parola, oturum açıkken değiştirildi. */
    PASSWORD_CHANGED,
    /** Parola, sıfırlama bağlantısıyla yenilendi. */
    PASSWORD_RESET,
    /** E-posta adresi değişti; bildirim <b>eski</b> adrese gider, hesabı ele geçirilen sahibi uyarır. */
    EMAIL_CHANGED,
    ACCOUNT_SUSPENDED,
    ACCOUNT_BANNED,
    ACCOUNT_DISABLED,
    ACCOUNT_REACTIVATED,
    /** İki adımlı doğrulama (TOTP) açıldı. */
    TWO_FACTOR_ENABLED,
    /** İki adımlı doğrulama kapatıldı: hesap ele geçirme belirtisi olabilir. */
    TWO_FACTOR_DISABLED,
    /** Tek kullanımlık kurtarma kodu kullanıldı (giriş ya da hassas işlem). */
    RECOVERY_CODE_USED,
    /** Hesaba Google ya da Steam kimliği bağlandı. */
    EXTERNAL_IDENTITY_LINKED,
    /** Hesap ilk kez doğrulanmış e-postayla açıldı (tek seferlik; adı Mail ile birebir). */
    WELCOME
}
