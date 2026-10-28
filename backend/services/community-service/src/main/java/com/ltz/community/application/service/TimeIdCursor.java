package com.ltz.community.application.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

import com.ltz.community.domain.exception.InvalidPostQueryException;

/**
 * (oluşturulma zamanı, id) ikilisini taşıyan opak keyset imleci; eskiden yeniye sıralı listeler (yorum, yanıt,
 * forum) için kullanılır. İmleç yalnız başlangıç konumunu belirler (yetki veya filtre taşımaz); bozuk imleç 400'dür.
 */
final class TimeIdCursor {

	record Position(Instant createdAt, UUID id) {
	}

	private static final String VERSION = "t1";
	private static final String INVALID = "The cursor is invalid.";

	private TimeIdCursor() {
	}

	static String encode(Instant createdAt, UUID id) {
		String raw = String.join("|", VERSION, createdAt.toString(), id.toString());
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

	static Position decode(String cursor) {
		try {
			String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
			String[] parts = raw.split("\\|", -1);
			if (parts.length != 3 || !VERSION.equals(parts[0])) {
				throw new InvalidPostQueryException(INVALID);
			}
			return new Position(Instant.parse(parts[1]), UUID.fromString(parts[2]));
		} catch (IllegalArgumentException | DateTimeParseException _) {
			throw new InvalidPostQueryException(INVALID);
		}
	}
}
