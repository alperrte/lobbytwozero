package com.ltz.community.application.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

import com.ltz.community.application.port.PostSearchCriteria.FeedPosition;
import com.ltz.community.domain.enums.PostSort;
import com.ltz.community.domain.exception.InvalidPostQueryException;

/**
 * Keyset sayfalama imleci: bir sayfanın son kaydının konumunu ve hangi sıralama için üretildiğini opak bir dize
 * olarak taşır. İmleç yalnız sorgunun başlangıç konumunu belirler (yetki veya filtre taşımaz); bozuk ya da başka bir
 * sıralama için üretilmiş imleç 400 ile reddedilir.
 */
final class PostCursor {

	private static final String VERSION = "v2";
	private static final String INVALID = "The cursor is invalid.";
	private static final String SORT_MISMATCH = "The cursor was issued for a different sort order.";

	private PostCursor() {
	}

	static String encode(PostSort sort, FeedPosition position) {
		String raw = String.join("|", VERSION, sort.name(), Integer.toString(position.score()),
				position.createdAt().toString(), position.id().toString());
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

	static FeedPosition decode(String cursor, PostSort expectedSort) {
		try {
			String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
			String[] parts = raw.split("\\|", -1);
			if (parts.length != 5 || !VERSION.equals(parts[0])) {
				throw new InvalidPostQueryException(INVALID);
			}
			if (PostSort.valueOf(parts[1]) != expectedSort) {
				throw new InvalidPostQueryException(SORT_MISMATCH);
			}
			int score = Integer.parseInt(parts[2]);
			if (score < 0) {
				throw new InvalidPostQueryException(INVALID);
			}
			return new FeedPosition(Instant.parse(parts[3]), UUID.fromString(parts[4]), score);
		} catch (IllegalArgumentException | DateTimeParseException _) {
			throw new InvalidPostQueryException(INVALID);
		}
	}
}
