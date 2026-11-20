package com.ltz.auth.infrastructure.client;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Map;

import com.ltz.auth.application.port.SecurityNotificationType;

/**
 * Geliştirme (Mailpit) için güvenlik bildirimi metinleri. Üretimde şablon Mail Service'indir (MAIL-08, MAIL-09). Metinler
 * parola, token veya bağlantı içermez; hesap sahibine ne olduğunu ve izinsizse ne yapacağını söyler (TR ve EN).
 * Her bildirim türü için tek satırlık bir kayıt vardır; eksik tür sınıf yüklenirken hata verir.
 */
final class SecurityNotificationTexts {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);
    private static final String BODY = """
            %1$s (%3$s)

            Bu işlemi siz yapmadıysanız hemen parolanızı sıfırlayın ve destek ile iletişime geçin.

            %2$s (%3$s)

            If you did not do this, reset your password immediately and contact support.
            """;

    private static final Map<SecurityNotificationType, Text> TEXTS = new EnumMap<>(SecurityNotificationType.class);

    static {
        add(SecurityNotificationType.PASSWORD_CHANGED, "LTZ parolanız değiştirildi / your password was changed",
                "LTZ hesabınızın parolası değiştirildi", "The password of your LTZ account was changed");
        add(SecurityNotificationType.PASSWORD_RESET, "LTZ parolanız sıfırlandı / your password was reset",
                "LTZ hesabınızın parolası sıfırlama bağlantısıyla yenilendi",
                "The password of your LTZ account was reset with a reset link");
        add(SecurityNotificationType.EMAIL_CHANGED,
                "LTZ e-posta adresiniz değiştirildi / your email address was changed",
                "LTZ hesabınızın e-posta adresi değiştirildi (bu bildirim eski adrese gönderildi)",
                "The email address of your LTZ account was changed (this notice went to the old address)");
        add(SecurityNotificationType.ACCOUNT_SUSPENDED, "LTZ hesabınız askıya alındı / your account was suspended",
                "LTZ hesabınız askıya alındı", "Your LTZ account was suspended");
        add(SecurityNotificationType.ACCOUNT_BANNED, "LTZ hesabınız yasaklandı / your account was banned",
                "LTZ hesabınız yasaklandı", "Your LTZ account was banned");
        add(SecurityNotificationType.ACCOUNT_DISABLED,
                "LTZ hesabınız devre dışı bırakıldı / your account was disabled",
                "LTZ hesabınız devre dışı bırakıldı", "Your LTZ account was disabled");
        add(SecurityNotificationType.ACCOUNT_REACTIVATED,
                "LTZ hesabınız yeniden açıldı / your account was reactivated",
                "LTZ hesabınız yeniden etkinleştirildi", "Your LTZ account was reactivated");
        add(SecurityNotificationType.TWO_FACTOR_ENABLED,
                "LTZ iki adımlı doğrulama açıldı / two-step verification was enabled",
                "LTZ hesabınızda iki adımlı doğrulama açıldı",
                "Two-step verification was enabled on your LTZ account");
        add(SecurityNotificationType.TWO_FACTOR_DISABLED,
                "LTZ iki adımlı doğrulama kapatıldı / two-step verification was disabled",
                "LTZ hesabınızda iki adımlı doğrulama kapatıldı",
                "Two-step verification was disabled on your LTZ account");
        add(SecurityNotificationType.RECOVERY_CODE_USED,
                "LTZ kurtarma kodu kullanıldı / a recovery code was used",
                "LTZ hesabınızda bir kurtarma kodu kullanıldı", "A recovery code was used on your LTZ account");
        add(SecurityNotificationType.EXTERNAL_IDENTITY_LINKED,
                "LTZ hesabınıza harici hesap bağlandı / an external account was linked",
                "LTZ hesabınıza bir Google ya da Steam hesabı bağlandı",
                "A Google or Steam account was linked to your LTZ account");
        add(SecurityNotificationType.WELCOME, "LTZ'ye hoş geldin / welcome to LTZ",
                "LTZ'ye hoş geldin, hesabın hazır", "Welcome to LTZ, your account is ready");
        add(SecurityNotificationType.ACCOUNT_DELETION_CANCELLED,
                "LTZ hesap silme talebi iptal edildi / account deletion was cancelled",
                "LTZ hesabınızın silme talebi iptal edildi, hesabınız silinmeyecek",
                "The deletion request for your LTZ account was cancelled; it will not be deleted");
        add(SecurityNotificationType.SUSPICIOUS_LOGIN,
                "LTZ hesabına yeni bir cihazdan giriş yapıldı / sign-in from a new device",
                "LTZ hesabınıza daha önce kullanmadığınız bir cihazdan giriş yapıldı",
                "Your LTZ account was signed in to from a device you have not used before");
        if (TEXTS.size() != SecurityNotificationType.values().length) {
            throw new IllegalStateException("Every SecurityNotificationType needs a development mail text");
        }
    }

    private SecurityNotificationTexts() {
    }

    static String subject(SecurityNotificationType type) {
        return TEXTS.get(type).subject();
    }

    static String body(SecurityNotificationType type, Instant occurredAt) {
        Text text = TEXTS.get(type);
        return BODY.formatted(text.turkish(), text.english(), WHEN.format(occurredAt));
    }

    private static void add(SecurityNotificationType type, String subject, String turkish, String english) {
        TEXTS.put(type, new Text(subject, turkish, english));
    }

    private record Text(String subject, String turkish, String english) {
    }
}
