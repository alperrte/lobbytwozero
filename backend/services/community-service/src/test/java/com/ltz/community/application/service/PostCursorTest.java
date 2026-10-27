package com.ltz.community.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.ltz.community.application.port.PostSearchCriteria.FeedPosition;
import com.ltz.community.domain.enums.PostSort;
import com.ltz.community.domain.exception.InvalidPostQueryException;

class PostCursorTest {

	private static final String TIME = "2026-10-21T10:00:00Z";

	private static String encodeRaw(String raw) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

	@ParameterizedTest
	@EnumSource(PostSort.class)
	void roundTripPreservesPositionScoreAndMicrosecondPrecision(PostSort sort) {
		FeedPosition position = new FeedPosition(Instant.parse("2026-10-21T10:15:30.123456Z"), UUID.randomUUID(), 42);

		String cursor = PostCursor.encode(sort, position);

		assertThat(cursor).matches("[A-Za-z0-9_-]+");
		assertThat(PostCursor.decode(cursor, sort)).isEqualTo(position);
	}

	@Test
	void cursorIssuedForAnotherSortOrderIsRejected() {
		String newest = PostCursor.encode(PostSort.NEWEST, new FeedPosition(Instant.parse(TIME), UUID.randomUUID(), 0));

		assertThatThrownBy(() -> PostCursor.decode(newest, PostSort.POPULAR))
				.isInstanceOf(InvalidPostQueryException.class)
				.hasMessageContaining("different sort");
		assertThatThrownBy(() -> PostCursor.decode(newest, PostSort.OLDEST))
				.isInstanceOf(InvalidPostQueryException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "not-base64!!", "bm9wZQ" })
	void malformedCursorsAreRejected(String cursor) {
		assertThatThrownBy(() -> PostCursor.decode(cursor, PostSort.NEWEST)).isInstanceOf(InvalidPostQueryException.class);
	}

	@Test
	void structurallyInvalidPayloadsAreRejected() {
		UUID id = UUID.randomUUID();
		for (String raw : new String[] {
				"v1|NEWEST|0|" + TIME + "|" + id,
				"v2|SIDEWAYS|0|" + TIME + "|" + id,
				"v2|NEWEST|many|" + TIME + "|" + id,
				"v2|NEWEST|-1|" + TIME + "|" + id,
				"v2|NEWEST|0|yesterday|" + id,
				"v2|NEWEST|0|" + TIME + "|not-a-uuid",
				"v2|NEWEST|0|" + TIME,
				"v2|NEWEST|0|" + TIME + "|" + id + "|extra" }) {
			String cursor = encodeRaw(raw);
			assertThatThrownBy(() -> PostCursor.decode(cursor, PostSort.NEWEST)).isInstanceOf(InvalidPostQueryException.class);
		}
	}
}
