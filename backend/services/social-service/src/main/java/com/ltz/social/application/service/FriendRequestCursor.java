package com.ltz.social.application.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

import com.ltz.social.domain.exception.SocialErrorCode;
import com.ltz.social.domain.exception.SocialException;

/**
 * Keyset sayfalama imleci: son görülen kaydın {@code (createdAt, id)} çifti. İstemci için opak bir metindir;
 * sunucu her gelen imleci sıkı doğrular, bozuk/uzun imleç 400 {@code INVALID_CURSOR} olur.
 */
record FriendRequestCursor(Instant createdAt, UUID id) {

    private static final String SEPARATOR = "|";
    private static final int MAX_ENCODED_LENGTH = 128;

    static FriendRequestCursor of(FriendRequestView last) {
        return new FriendRequestCursor(last.createdAt(), last.id());
    }

    String encode() {
        String raw = createdAt + SEPARATOR + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    static FriendRequestCursor decode(String encoded) {
        if (encoded.isEmpty() || encoded.length() > MAX_ENCODED_LENGTH) {
            throw invalid();
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            int separator = raw.indexOf(SEPARATOR);
            if (separator < 0) {
                throw invalid();
            }
            return new FriendRequestCursor(Instant.parse(raw.substring(0, separator)),
                    UUID.fromString(raw.substring(separator + 1)));
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw invalid();
        }
    }

    private static SocialException invalid() {
        return new SocialException(SocialErrorCode.INVALID_CURSOR);
    }
}
