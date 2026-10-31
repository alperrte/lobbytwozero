package com.ltz.mail.application.service;

import java.util.UUID;

import com.ltz.mail.domain.enums.MailLocale;
import com.ltz.mail.domain.enums.MailType;

/**
 * Kuyruğa alma komutu. {@code locale} {@code null} ise e-posta iki dilli gider. {@code toString()} alıcıyı ve içeriği gizler.
 */
public record MailCommand(UUID idempotencyKey, UUID accountId, MailType type, String recipientEmail,
                          MailLocale locale, MailPayload payload) {

    @Override
    public String toString() {
        return "MailCommand[idempotencyKey=" + idempotencyKey + ", type=" + type + ", recipientEmail=***]";
    }
}
