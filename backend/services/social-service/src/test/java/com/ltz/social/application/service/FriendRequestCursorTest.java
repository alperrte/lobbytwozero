package com.ltz.social.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ltz.social.domain.exception.SocialErrorCode;
import com.ltz.social.domain.exception.SocialException;

class FriendRequestCursorTest {

    @Test
    void kodlanipCozulur() {
        FriendRequestCursor cursor = new FriendRequestCursor(Instant.parse("2026-10-22T10:00:00.123456Z"),
                UUID.randomUUID());

        assertThat(FriendRequestCursor.decode(cursor.encode())).isEqualTo(cursor);
    }

    @Test
    void bozukImecler_INVALID_CURSOR() {
        String noSeparator = encode("2026-10-22T10:00:00Z");
        String badInstant = encode("yarin|" + UUID.randomUUID());
        String badUuid = encode("2026-10-22T10:00:00Z|not-a-uuid");

        for (String invalid : new String[] {"", "bozuk", "%%%", noSeparator, badInstant, badUuid, "A".repeat(129)}) {
            assertThatThrownBy(() -> FriendRequestCursor.decode(invalid)).isInstanceOfSatisfying(SocialException.class,
                    exception -> assertThat(exception.errorCode()).isEqualTo(SocialErrorCode.INVALID_CURSOR));
        }
    }

    private static String encode(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
