package com.ltz.messaging.application.service;

import java.util.UUID;

/** {@code message_hidden} olayının yükü (MSG-25): kullanıcının diğer cihazları mesajı görünümden kaldırır. */
public record MessageHiddenView(UUID conversationId, UUID messageId) {
}
