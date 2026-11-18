package com.ltz.event.domain.enums;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/** Enum değerleri ile veritabanı CHECK kısıtı (yürürlükteki son migration) arasındaki tutarlılığı denetler. */
class EventTypeTest {

	private static final Pattern CHECK = Pattern.compile(
			"constraint\\s+ck_events_type\\s+check\\s*\\(\\s*type\\s+in\\s*\\(([^)]*)\\)\\s*\\)",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern CHECK_ADD = Pattern.compile(
			"add\\s+constraint\\s+ck_events_type\\s+check\\s*\\(\\s*type\\s+in\\s*\\(([^)]*)\\)\\s*\\)",
			Pattern.CASE_INSENSITIVE);

	private static String migration(String name) throws IOException {
		try (InputStream in = EventTypeTest.class.getResourceAsStream("/db/migration/" + name)) {
			assertThat(in).as(name).isNotNull();
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static Set<String> valuesIn(Matcher matcher) {
		assertThat(matcher.find()).isTrue();
		Set<String> values = new LinkedHashSet<>();
		for (String value : matcher.group(1).split(",")) {
			values.add(value.trim().replace("'", ""));
		}
		return values;
	}

	@Test
	void enumValuesMatchTheLatestCheckConstraint() throws IOException {
		Set<String> constraint = valuesIn(CHECK_ADD.matcher(migration("V112__add_poll_event_type.sql")));

		assertThat(Arrays.stream(EventType.values()).map(Enum::name)).containsExactlyInAnyOrderElementsOf(constraint);
	}

	@Test
	void initialMigrationIsAStrictSubsetOfTheCurrentTypes() throws IOException {
		Set<String> initial = valuesIn(CHECK.matcher(migration("V111__create_events_table.sql")));

		assertThat(initial).containsExactlyInAnyOrder("TOURNAMENT", "GAME_NIGHT", "COMMUNITY", "OTHER");
		assertThat(Arrays.stream(EventType.values()).map(Enum::name)).containsAll(initial);
	}

	@Test
	void pollIsASeparateEventType() {
		assertThat(EventType.valueOf("POLL")).isNotIn(EventType.OTHER, EventType.COMMUNITY);
		assertThat(EventType.values()).hasSize(5);
	}
}
