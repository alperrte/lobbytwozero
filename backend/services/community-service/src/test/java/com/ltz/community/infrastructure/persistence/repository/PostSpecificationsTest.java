package com.ltz.community.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PostSpecificationsTest {

	@Test
	void containsPatternLowercasesAndWrapsText() {
		assertThat(PostSpecifications.containsPattern("Elden RING")).isEqualTo("%elden ring%");
	}

	@Test
	void containsPatternEscapesLikeWildcardsAndEscapeCharacter() {
		assertThat(PostSpecifications.containsPattern("100%")).isEqualTo("%100\\%%");
		assertThat(PostSpecifications.containsPattern("a_b")).isEqualTo("%a\\_b%");
		assertThat(PostSpecifications.containsPattern("c:\\x")).isEqualTo("%c:\\\\x%");
		assertThat(PostSpecifications.containsPattern("%_\\")).isEqualTo("%\\%\\_\\\\%");
	}
}
