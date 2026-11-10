package com.ltz.mail.application.service;

import java.util.UUID;

import com.ltz.mail.domain.enums.MailLocale;

/**
 * Platform kapısı komutu. {@code locale} {@code null} ise {@code tr} kullanılır (bilgilendirme tek dilli gider: içeriği
 * çağıran kendi yerelleştirir). {@code toString()} içeriği gizler.
 */
public record NoticeCommand(UUID idempotencyKey, UUID accountId, MailLocale locale, NoticeContent content) {

    @Override
    public String toString() {
        return "NoticeCommand[idempotencyKey=" + idempotencyKey + ", content=***]";
    }
}
