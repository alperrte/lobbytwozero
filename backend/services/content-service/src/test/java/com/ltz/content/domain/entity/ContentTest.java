package com.ltz.content.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ltz.content.domain.enums.ContentStatus;
import com.ltz.content.domain.enums.ContentType;

class ContentTest {

	private static final UUID AUTHOR = UUID.fromString("0198a3c2-7b1e-7d44-9a55-3f1c2b4d5e66");
	private static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");
	private static final String HASH = "a".repeat(64);

	private static Content draft() {
		return Content.draft(AUTHOR, ContentType.NEWS, "tr", HASH, NOW);
	}

	@Test
	void everyNewContentStartsAsDraftWithTheGivenOwnerAndType() {
		Content content = draft();

		assertThat(content.getStatus()).isEqualTo(ContentStatus.DRAFT);
		assertThat(content.isDraft()).isTrue();
		assertThat(content.getAuthorId()).isEqualTo(AUTHOR);
		assertThat(content.getType()).isEqualTo(ContentType.NEWS);
		assertThat(content.getDefaultLocale()).isEqualTo("tr");
		assertThat(content.getCreatedAt()).isEqualTo(NOW);
		assertThat(content.getUpdatedAt()).isEqualTo(NOW);
		assertThat(content.isDeleted()).isFalse();
	}

	@Test
	void ownershipIsDecidedByTheAuthorId() {
		Content content = draft();

		assertThat(content.isOwnedBy(AUTHOR)).isTrue();
		assertThat(content.isOwnedBy(UUID.randomUUID())).isFalse();
	}

	@Test
	void reviseUpdatesTheHashAndTheChangeTimeOnly() {
		Content content = draft();
		Instant later = NOW.plusSeconds(60);

		content.revise("b".repeat(64), later);

		assertThat(content.getContentHash()).isEqualTo("b".repeat(64));
		assertThat(content.getUpdatedAt()).isEqualTo(later);
		assertThat(content.getCreatedAt()).isEqualTo(NOW);
		assertThat(content.getAuthorId()).isEqualTo(AUTHOR);
		assertThat(content.getStatus()).isEqualTo(ContentStatus.DRAFT);
	}

	@Test
	void deletionMarksTheRowErasesTheTextFingerprintAndKeepsTheStatus() {
		Content content = draft();
		Instant later = NOW.plusSeconds(5);

		content.markDeleted(later);

		assertThat(content.isDeleted()).isTrue();
		assertThat(content.getDeletedAt()).isEqualTo(later);
		assertThat(content.getStatus()).isEqualTo(ContentStatus.DRAFT);
		assertThat(content.isDeletableByOwner()).isFalse();
		assertThat(content.getContentHash()).isEqualTo(Content.ERASED_HASH).isEqualTo("0".repeat(64));
		assertThat(content.getAuthorId()).isEqualTo(AUTHOR);
		assertThat(content.getCreatedAt()).isEqualTo(NOW);
	}

	@Test
	void ownerCanDeleteEverythingExceptModeratorRemovedContent() {
		for (ContentStatus status : ContentStatus.values()) {
			Content content = draft();
			ReflectionTestUtils.setField(content, "status", status);

			assertThat(content.isDeletableByOwner()).isEqualTo(status != ContentStatus.REMOVED);
		}
	}

	@Test
	void contentTypeJsonCreatorAcceptsOnlyExactUppercaseNames() {
		assertThat(ContentType.fromJson("ANNOUNCEMENT")).isEqualTo(ContentType.ANNOUNCEMENT);
		for (String invalid : new String[] { "announcement", "1", "", null, "Official" }) {
			assertThatThrownBy(() -> ContentType.fromJson(invalid)).isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Test
	void translationRewriteReplacesAllTexts() {
		UUID contentId = UUID.randomUUID();
		ContentTranslation translation = ContentTranslation.of(contentId, "tr", "Eski", "özet", "gövde");

		translation.rewrite("Yeni", null, null);

		assertThat(translation.getId()).isEqualTo(new ContentTranslationId(contentId, "tr"));
		assertThat(translation.getTitle()).isEqualTo("Yeni");
		assertThat(translation.getSummary()).isNull();
		assertThat(translation.getBody()).isNull();
	}

	@Test
	void imageAttachRecordsTheSizeOfTheStoredBytes() {
		ContentImage image = ContentImage.attach(UUID.randomUUID(), new byte[] { 1, 2, 3 }, "image/png", 10, 20, 2, NOW);

		assertThat(image.getSizeBytes()).isEqualTo(3);
		assertThat(image.getSortOrder()).isEqualTo(2);
		assertThat(image.getMimeType()).isEqualTo("image/png");
		assertThat(image.getCreatedAt()).isEqualTo(NOW);
	}
	@Test
	void removeMovesPublishedContentToRemovedAndKeepsTheDecisionDetails() {
		UUID moderator = UUID.randomUUID();
		Content content = draft();
		content.publish(NOW);
		content.feature(UUID.randomUUID(), NOW);
		Instant removedAt = NOW.plusSeconds(60);

		content.remove(moderator, "Kurallara aykırı", removedAt);

		assertThat(content.getStatus()).isEqualTo(ContentStatus.REMOVED);
		assertThat(content.isRemoved()).isTrue();
		assertThat(content.isPublished()).isFalse();
		assertThat(content.getRemovedAt()).isEqualTo(removedAt);
		assertThat(content.getRemovedBy()).isEqualTo(moderator);
		assertThat(content.getRemovedReason()).isEqualTo("Kurallara aykırı");
		assertThat(content.getUpdatedAt()).isEqualTo(removedAt);
		assertThat(content.isFeatured()).isFalse();
		assertThat(content.getFeaturedAt()).isNull();
		assertThat(content.getFeaturedBy()).isNull();
		assertThat(content.getPublishedAt()).isEqualTo(NOW);
		assertThat(content.isEditableByOwner()).isFalse();
		assertThat(content.isDeletableByOwner()).isFalse();
	}

	@Test
	void restoreBringsRemovedContentBackWithoutChangingThePublishTime() {
		Content content = draft();
		content.publish(NOW);
		content.remove(UUID.randomUUID(), "Neden", NOW.plusSeconds(60));
		Instant restoredAt = NOW.plusSeconds(120);

		content.restore(restoredAt);

		assertThat(content.getStatus()).isEqualTo(ContentStatus.PUBLISHED);
		assertThat(content.isRemoved()).isFalse();
		assertThat(content.isPublished()).isTrue();
		assertThat(content.getRemovedAt()).isNull();
		assertThat(content.getRemovedBy()).isNull();
		assertThat(content.getRemovedReason()).isNull();
		assertThat(content.getPublishedAt()).isEqualTo(NOW);
		assertThat(content.getUpdatedAt()).isEqualTo(restoredAt);
		assertThat(content.isFeatured()).isFalse();
	}

	@Test
	void draftsAndPublishedContentAreNotRemoved() {
		Content content = draft();
		assertThat(content.isRemoved()).isFalse();

		content.publish(NOW);
		assertThat(content.isRemoved()).isFalse();
	}
}
