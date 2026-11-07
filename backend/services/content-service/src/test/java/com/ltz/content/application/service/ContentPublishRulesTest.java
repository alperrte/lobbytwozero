package com.ltz.content.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.ltz.content.application.service.ContentPublishRules.Issue;

/** Yayın kuralları taslak kurallarından ayrıdır: taslak kaydedilebilir ama yayınlanamaz olabilir. */
class ContentPublishRulesTest {

	private static String text(int length) {
		return "a".repeat(length);
	}

	@Test
	void theMvpMinimumsAreFiveForTheTitleAndThirtyForTheBody() {
		assertThat(ContentPublishRules.MIN_TITLE_LENGTH).isEqualTo(5);
		assertThat(ContentPublishRules.MIN_BODY_LENGTH).isEqualTo(30);
	}

	@Test
	void contentAtTheMinimumsHasNoIssues() {
		assertThat(ContentPublishRules.check(text(5), text(30))).isEmpty();
	}

	@Test
	void aBareTitleIsAValidDraftButNotPublishable() {
		assertThat(ContentPublishRules.check("Hi", null)).containsExactly(Issue.TITLE_TOO_SHORT, Issue.BODY_TOO_SHORT);
	}

	@Test
	void eachMinimumIsReportedSeparately() {
		assertThat(ContentPublishRules.check(text(4), text(30))).containsExactly(Issue.TITLE_TOO_SHORT);
		assertThat(ContentPublishRules.check(text(5), text(29))).containsExactly(Issue.BODY_TOO_SHORT);
	}

	@Test
	void theSummaryHasNoMinimum() {
		// check yalnız başlık ve gövdeyi alır: özet yayın için zorunlu ya da uzunluk sınırlı değildir.
		assertThat(ContentPublishRules.check(text(5), text(30))).isEmpty();
	}

	@Test
	void surroundingWhitespaceDoesNotCountTowardsLength() {
		assertThat(ContentPublishRules.check("   " + text(4) + "   ", "  " + text(30) + "  "))
				.containsExactly(Issue.TITLE_TOO_SHORT);
		assertThat(ContentPublishRules.check(text(5), "\n\t " + text(29) + " \n"))
				.containsExactly(Issue.BODY_TOO_SHORT);
	}

	@Test
	void aBlankTitleOrBodyCountsAsEmpty() {
		assertThat(ContentPublishRules.check("     ", "          ")).containsExactly(Issue.TITLE_TOO_SHORT, Issue.BODY_TOO_SHORT);
	}
}
