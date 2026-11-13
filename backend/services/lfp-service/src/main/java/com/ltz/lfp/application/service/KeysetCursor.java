package com.ltz.lfp.application.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

import com.ltz.lfp.domain.exception.LfpErrorCode;
import com.ltz.lfp.domain.exception.LfpException;

/**
 * Keyset sayfalama imleci: son görülen ilanın {@code (createdAt, id)} çifti. İstemci için opak bir metindir; sunucu
 * her gelen imleci sıkı doğrular, bozuk/uzun imleç 400 {@code INVALID_CURSOR} olur. Süzgeçler imlece gömülmez; istemci
 * sonraki sayfada aynı süzgeçleri gönderir.
 */
record KeysetCursor(Instant createdAt, UUID id) {

    private static final String SEPARATOR = "|";
    private static final int MAX_ENCODED_LENGTH = 128;

    String encode() {
        String raw = createdAt + SEPARATOR + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    static KeysetCursor decode(String encoded) {
        if (encoded.isEmpty() || encoded.length() > MAX_ENCODED_LENGTH) {
            throw invalid();
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            int separator = raw.indexOf(SEPARATOR);
            if (separator < 0) {
                throw invalid();
            }
            return new KeysetCursor(Instant.parse(raw.substring(0, separator)),
                    UUID.fromString(raw.substring(separator + 1)));
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw invalid();
        }
    }

    private static LfpException invalid() {
        return new LfpException(LfpErrorCode.INVALID_CURSOR);
    }
}
