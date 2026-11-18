package com.ltz.event.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EventSearchTest {

	@Test
	void wrapsPlainTextAsAContainsPattern() {
		assertThat(EventSearch.containsPattern("turnuva")).isEqualTo("%turnuva%");
	}

	@Test
	void likeWildcardsAndTheEscapeCharacterAreMatchedLiterally() {
		assertThat(EventSearch.containsPattern("100%")).isEqualTo("%100!%%");
		assertThat(EventSearch.containsPattern("a_b")).isEqualTo("%a!_b%");
		assertThat(EventSearch.containsPattern("!")).isEqualTo("%!!%");
		assertThat(EventSearch.containsPattern("%_!")).isEqualTo("%!%!_!!%");
	}

	@Test
	void unicodeAndSpacesAreKept() {
		assertThat(EventSearch.containsPattern("Öğrenci Gecesi İ")).isEqualTo("%Öğrenci Gecesi İ%");
	}
}
