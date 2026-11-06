package com.ltz.content.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ltz.content.application.port.ContentFeedPort.Position;
import com.ltz.content.domain.exception.InvalidContentQueryException;

class PublishedCursorTest {

	private static final Instant AT = Instant.parse("2026-11-07T09:00:00.123456Z");
	private static final UUID ID = UUID.fromString("0198a3c2-9999-7d44-9a55-3f1c2b4d5e66");

	private static String encode(String raw) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void aCursorRoundTripsTheExactPosition() {
		Position position = PublishedCursor.decode(PublishedCursor.encode(AT, ID));

		assertThat(position).isEqualTo(new Position(AT, ID));
	}

	@Test
	void aCursorIsOpaqueUrlSafeText() {
		assertThat(PublishedCursor.encode(AT, ID)).matches("[A-Za-z0-9_-]+");
	}

	@Test
	void malformedCursorsAreRejected() {
		String[] broken = { "", "not base64 !!", encode("p1|only-two"), encode("p2|" + AT + "|" + ID),
				encode("p1|yarin|" + ID), encode("p1|" + AT + "|not-a-uuid"), encode("p1|" + AT + "|" + ID + "|extra"),
				encode("t1|" + AT + "|" + ID) };
		for (String cursor : broken) {
			assertThatThrownBy(() -> PublishedCursor.decode(cursor)).isInstanceOf(InvalidContentQueryException.class);
		}
	}
}
