package com.ltz.auth.domain.enums;

/**
 * Auth'un diğer servislere ilettiği olaylar. {@code eventName} zarftaki {@code eventType} değeridir; payload yalnız hesap
 * kimliği ve en fazla bir değer taşır (durum veya herkese açık kullanıcı adı). E-posta ve kişisel veri taşınmaz.
 */
public enum AuthEventType {
    /** Yönetici hesabı askıya aldı, yasakladı, devre dışı bıraktı veya yeniden açtı. Değer: yeni durum. */
    ACCOUNT_STATUS_CHANGED("AccountStatusChanged", true),
    /** Hesap ACTIVE oldu (e-posta doğrulandı veya doğrulanmış sosyal giriş). Değer: kullanıcı adı. */
    USER_REGISTERED("UserRegistered", true),
    /** Hesabın e-posta adresi değişti. Değer yok; yeni adres istenirse Auth'tan {@code account:read} ile sorulur. */
    ACCOUNT_EMAIL_CHANGED("AccountEmailChanged", false);

    private final String eventName;
    private final boolean carriesValue;

    AuthEventType(String eventName, boolean carriesValue) {
        this.eventName = eventName;
        this.carriesValue = carriesValue;
    }

    public String eventName() {
        return eventName;
    }

    public boolean carriesValue() {
        return carriesValue;
    }
}
