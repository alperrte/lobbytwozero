package com.ltz.auth.application.port;

import java.time.Instant;
import java.util.UUID;

/**
 * Bildirim e-postası isteği. Parola, token, kod veya bağlantı taşımaz; yalnız ne olduğunu ve ne zaman olduğunu söyler.
 * {@code idempotencyKey} Mail Service'in tekrarlanan isteği tekilleştirmesi içindir. Alıcı adresi loglanmaz.
 */
public record SecurityNotificationMail(UUID idempotencyKey, UUID accountId, String recipientEmail,
                                       SecurityNotificationType type, Instant occurredAt, String requestLocale) {

    public SecurityNotificationMail(UUID idempotencyKey, UUID accountId, String recipientEmail,
                                    SecurityNotificationType type, Instant occurredAt) {
        this(idempotencyKey, accountId, recipientEmail, type, occurredAt, null);
    }

    public SecurityNotificationMail withRequestLocale(String locale) {
        return new SecurityNotificationMail(idempotencyKey, accountId, recipientEmail, type, occurredAt, locale);
    }

    @Override
    public String toString() {
        return "SecurityNotificationMail[idempotencyKey=" + idempotencyKey + ", type=" + type + ", recipientEmail=***]";
    }
}
