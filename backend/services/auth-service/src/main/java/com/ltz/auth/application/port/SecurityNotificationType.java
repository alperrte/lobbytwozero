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
    ACCOUNT_REACTIVATED
}
