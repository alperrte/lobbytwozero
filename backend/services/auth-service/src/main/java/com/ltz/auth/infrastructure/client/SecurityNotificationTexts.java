package com.ltz.auth.infrastructure.client;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import com.ltz.auth.application.port.SecurityNotificationType;

/**
 * Geliştirme (Mailpit) için güvenlik bildirimi metinleri. Üretimde şablon Mail Service'indir (MAIL-08, MAIL-09). Metinler
 * parola, token veya bağlantı içermez; hesap sahibine ne olduğunu ve izinsizse ne yapacağını söyler (TR ve EN).
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

    private SecurityNotificationTexts() {
    }

    static String subject(SecurityNotificationType type) {
        return switch (type) {
            case PASSWORD_CHANGED -> "LTZ parolanız değiştirildi / your password was changed";
            case PASSWORD_RESET -> "LTZ parolanız sıfırlandı / your password was reset";
            case EMAIL_CHANGED -> "LTZ e-posta adresiniz değiştirildi / your email address was changed";
            case ACCOUNT_SUSPENDED -> "LTZ hesabınız askıya alındı / your account was suspended";
            case ACCOUNT_BANNED -> "LTZ hesabınız yasaklandı / your account was banned";
            case ACCOUNT_DISABLED -> "LTZ hesabınız devre dışı bırakıldı / your account was disabled";
            case ACCOUNT_REACTIVATED -> "LTZ hesabınız yeniden açıldı / your account was reactivated";
        };
    }

    static String body(SecurityNotificationType type, Instant occurredAt) {
        String when = WHEN.format(occurredAt);
        return BODY.formatted(turkish(type), english(type), when);
    }

    private static String turkish(SecurityNotificationType type) {
        return switch (type) {
            case PASSWORD_CHANGED -> "LTZ hesabınızın parolası değiştirildi";
            case PASSWORD_RESET -> "LTZ hesabınızın parolası sıfırlama bağlantısıyla yenilendi";
            case EMAIL_CHANGED -> "LTZ hesabınızın e-posta adresi değiştirildi (bu bildirim eski adrese gönderildi)";
            case ACCOUNT_SUSPENDED -> "LTZ hesabınız askıya alındı";
            case ACCOUNT_BANNED -> "LTZ hesabınız yasaklandı";
            case ACCOUNT_DISABLED -> "LTZ hesabınız devre dışı bırakıldı";
            case ACCOUNT_REACTIVATED -> "LTZ hesabınız yeniden etkinleştirildi";
        };
    }

    private static String english(SecurityNotificationType type) {
        return switch (type) {
            case PASSWORD_CHANGED -> "The password of your LTZ account was changed";
            case PASSWORD_RESET -> "The password of your LTZ account was reset with a reset link";
            case EMAIL_CHANGED -> "The email address of your LTZ account was changed (this notice went to the old address)";
            case ACCOUNT_SUSPENDED -> "Your LTZ account was suspended";
            case ACCOUNT_BANNED -> "Your LTZ account was banned";
            case ACCOUNT_DISABLED -> "Your LTZ account was disabled";
            case ACCOUNT_REACTIVATED -> "Your LTZ account was reactivated";
        };
    }
}
