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

/** Enum değerleri ile {@code event_platforms} CHECK kısıtı (V115) arasındaki tutarlılığı denetler. */
class EventPlatformTest {

	private static final Pattern CHECK = Pattern.compile(
			"constraint\\s+ck_event_platforms_platform\\s+check\\s*\\(\\s*platform\\s+in\\s*\\(([^)]*)\\)\\s*\\)",
			Pattern.CASE_INSENSITIVE);

	private static Set<String> constraintValues() throws IOException {
		try (InputStream in = EventPlatformTest.class.getResourceAsStream("/db/migration/V115__create_event_platforms_table.sql")) {
			assertThat(in).isNotNull();
			Matcher matcher = CHECK.matcher(new String(in.readAllBytes(), StandardCharsets.UTF_8));
			assertThat(matcher.find()).isTrue();
			Set<String> values = new LinkedHashSet<>();
			for (String value : matcher.group(1).split(",")) {
				values.add(value.trim().replace("'", ""));
			}
			return values;
		}
	}

	@Test
	void enumValuesMatchTheCheckConstraint() throws IOException {
		assertThat(Arrays.stream(EventPlatform.values()).map(Enum::name))
				.containsExactlyInAnyOrderElementsOf(constraintValues());
	}

	@Test
	void vocabularyIsTheContentServicePlatformList() {
		assertThat(Arrays.stream(EventPlatform.values()).map(Enum::name))
				.containsExactly("PC", "PLAYSTATION", "XBOX", "NINTENDO_SWITCH", "MOBILE", "VR");
	}
}
