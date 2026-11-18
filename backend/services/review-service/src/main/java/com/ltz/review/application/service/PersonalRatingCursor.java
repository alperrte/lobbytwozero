package com.ltz.review.application.service;

import com.ltz.review.domain.exception.ReviewException;
import com.ltz.review.infrastructure.persistence.repository.AccountWriteLock;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

record PersonalRatingCursor(UUID after, Instant before, int page) {
    static final int MAX_PAGE = 10;
    private static final int MAX_LENGTH = 200;

    String encode(UUID owner) {
        String text = String.join("/", AccountWriteLock.hash(owner), after.toString(), before.toString(), Integer.toString(page));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.US_ASCII));
    }

    static PersonalRatingCursor decode(String encoded, UUID owner) {
        if (encoded.isEmpty() || encoded.length() > MAX_LENGTH) throw invalid();
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.US_ASCII);
            String[] fields = decoded.split("/", -1);
            if (fields.length != 4 || !AccountWriteLock.hash(owner).equals(fields[0])) throw invalid();
            var cursor = new PersonalRatingCursor(UUID.fromString(fields[1]), Instant.parse(fields[2]), Integer.parseInt(fields[3]));
            if (!cursor.isValid(encoded, owner)) throw invalid();
            return cursor;
        } catch (IllegalArgumentException | DateTimeParseException _) {
            throw invalid();
        }
    }

    private boolean isValid(String encoded, UUID owner) {
        return page >= 2 && page <= MAX_PAGE && !before.isBefore(Instant.EPOCH)
                && !before.isAfter(Instant.now().plusSeconds(60)) && encode(owner).equals(encoded);
    }

    private static ReviewException invalid() {
        return new ReviewException(400, "INVALID_RATINGS_CURSOR", "Puan sayfası imleci geçersiz.");
    }
}
