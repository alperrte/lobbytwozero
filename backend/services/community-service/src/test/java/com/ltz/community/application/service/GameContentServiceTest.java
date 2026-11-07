package com.ltz.community.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class GameContentServiceTest {

	@Test
	void shortBodyKeepsItsTextWithCollapsedWhitespace() {
		assertThat(GameContentService.excerpt("  ilk\n\nsatır \t ikinci   satır  ")).isEqualTo("ilk satır ikinci satır");
	}

	@Test
	void nullOrBlankBodyGivesAnEmptyExcerpt() {
		assertThat(GameContentService.excerpt(null)).isEmpty();
		assertThat(GameContentService.excerpt("  \n\t ")).isEmpty();
	}

	@Test
	void bodyOfExactlyTheLimitIsNotTruncated() {
		String exact = "a".repeat(GameContentService.EXCERPT_MAX_LENGTH);

		assertThat(GameContentService.excerpt(exact)).isEqualTo(exact);
	}

	@Test
	void longerBodyIsCutAtTheLimitAndEndsWithAnEllipsis() {
		String excerpt = GameContentService.excerpt("b".repeat(GameContentService.EXCERPT_MAX_LENGTH + 50));

		assertThat(excerpt).isEqualTo("b".repeat(GameContentService.EXCERPT_MAX_LENGTH) + "…");
	}

	@Test
	void cutNeverSplitsASurrogatePairAndDropsTrailingSpaceBeforeTheEllipsis() {
		String emojis = "😀".repeat(GameContentService.EXCERPT_MAX_LENGTH + 5);
		String withSpaceAtCut = "c".repeat(GameContentService.EXCERPT_MAX_LENGTH - 1) + " " + "d".repeat(10);

		String cutEmojis = GameContentService.excerpt(emojis);
		String cutSpace = GameContentService.excerpt(withSpaceAtCut);

		assertThat(cutEmojis.codePointCount(0, cutEmojis.length())).isEqualTo(GameContentService.EXCERPT_MAX_LENGTH + 1);
		assertThat(cutEmojis).endsWith("…").doesNotContain("�");
		assertThat(cutSpace).isEqualTo("c".repeat(GameContentService.EXCERPT_MAX_LENGTH - 1) + "…");
	}

	@Test
	void excerptKeepsMarkupAsPlainTextAndNeverInterpretsIt() {
		assertThat(GameContentService.excerpt("<script>alert(1)</script>")).isEqualTo("<script>alert(1)</script>");
	}

	@Test
	void detailPathPointsToTheGameContentDetailEndpoint() {
		UUID gameId = UUID.fromString("0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66");
		UUID contentId = UUID.fromString("0198a3c2-9999-7d44-9a55-3f1c2b4d5e66");

		assertThat(GameContentService.detailPath(gameId, contentId)).isEqualTo(
				"/api/v1/community/games/0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66/content/0198a3c2-9999-7d44-9a55-3f1c2b4d5e66");
	}
}
