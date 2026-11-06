package com.ltz.content.application.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

import com.ltz.content.application.port.ContentFeedPort.Position;
import com.ltz.content.domain.exception.InvalidContentQueryException;

/**
 * (yayınlanma zamanı, id) ikilisini taşıyan opak keyset imleci. İmleç yalnız başlangıç konumunu belirler (yetki ya da filtre
 * taşımaz); bozuk imleç 400'dür. Sonraki sayfa istenirken aynı filtreler verilmelidir.
 */
final class PublishedCursor {

	private static final String VERSION = "p1";
	private static final String INVALID = "The cursor is invalid.";

	private PublishedCursor() {
	}

	static String encode(Instant publishedAt, UUID id) {
		String raw = String.join("|", VERSION, publishedAt.toString(), id.toString());
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

	static Position decode(String cursor) {
		try {
			String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
			String[] parts = raw.split("\\|", -1);
			if (parts.length != 3 || !VERSION.equals(parts[0])) {
				throw new InvalidContentQueryException(INVALID);
			}
			return new Position(Instant.parse(parts[1]), UUID.fromString(parts[2]));
		} catch (IllegalArgumentException | DateTimeParseException _) {
			throw new InvalidContentQueryException(INVALID);
		}
	}
}
