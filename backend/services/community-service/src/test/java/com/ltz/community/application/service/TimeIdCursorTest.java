package com.ltz.community.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.ltz.community.domain.exception.InvalidPostQueryException;

class TimeIdCursorTest {

	private static String encodeRaw(String raw) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void roundTripsPosition() {
		Instant createdAt = Instant.parse("2026-10-21T10:15:30.123456Z");
		UUID id = UUID.randomUUID();

		TimeIdCursor.Position position = TimeIdCursor.decode(TimeIdCursor.encode(createdAt, id));

		assertThat(position.createdAt()).isEqualTo(createdAt);
		assertThat(position.id()).isEqualTo(id);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "***", "bozuk" })
	void rejectsGarbage(String cursor) {
		assertThatThrownBy(() -> TimeIdCursor.decode(cursor)).isInstanceOf(InvalidPostQueryException.class);
	}

	@Test
	void rejectsWrongVersionShapeAndValues() {
		String id = UUID.randomUUID().toString();
		String wrongVersion = encodeRaw("t2|2026-10-21T10:00:00Z|" + id);
		String missingPart = encodeRaw("t1|2026-10-21T10:00:00Z");
		String badInstant = encodeRaw("t1|dun|" + id);
		String badId = encodeRaw("t1|2026-10-21T10:00:00Z|degil");
		assertThatThrownBy(() -> TimeIdCursor.decode(wrongVersion))
				.isInstanceOf(InvalidPostQueryException.class);
		assertThatThrownBy(() -> TimeIdCursor.decode(missingPart))
				.isInstanceOf(InvalidPostQueryException.class);
		assertThatThrownBy(() -> TimeIdCursor.decode(badInstant))
				.isInstanceOf(InvalidPostQueryException.class);
		assertThatThrownBy(() -> TimeIdCursor.decode(badId))
				.isInstanceOf(InvalidPostQueryException.class);
	}
}
