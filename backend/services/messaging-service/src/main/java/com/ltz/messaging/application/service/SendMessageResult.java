package com.ltz.messaging.application.service;

/** {@code created}: yeni mesaj (201) mı, aynı {@code clientMessageId} ile yapılmış yeniden deneme (200) mi. */
public record SendMessageResult(MessageView message, boolean created) {
}
