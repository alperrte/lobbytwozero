package com.ltz.messaging.application.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

import com.ltz.messaging.domain.exception.MessagingErrorCode;
import com.ltz.messaging.domain.exception.MessagingException;

/**
 * Sohbet listesinin keyset imleci: son görülen sohbetin {@code (lastActivityAt, id)} çifti. İstemci için opaktır;
 * bozuk ya da aşırı uzun imleç {@code 400 INVALID_CURSOR} olur.
 */
record ActivityCursor(Instant activityAt, UUID id) {

    private static final int MAX_LENGTH = 128;

    String encode() {
        byte[] raw = (activityAt + "/" + id).getBytes(StandardCharsets.UTF_8);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    static ActivityCursor decode(String value) {
        if (value.isEmpty() || value.length() > MAX_LENGTH) {
            throw new MessagingException(MessagingErrorCode.INVALID_CURSOR);
        }
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split("/", -1);
            if (parts.length != 2) {
                throw new MessagingException(MessagingErrorCode.INVALID_CURSOR);
            }
            return new ActivityCursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw new MessagingException(MessagingErrorCode.INVALID_CURSOR, exception);
        }
    }
}
