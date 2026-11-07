package com.ltz.content.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ltz.content.api.dto.response.ContentResponse;
import com.ltz.content.application.mapper.ContentMapper;
import com.ltz.content.application.port.CurrentUserPort;
import com.ltz.content.domain.entity.Content;
import com.ltz.content.domain.entity.ContentTranslation;
import com.ltz.content.domain.enums.ContentStatus;
import com.ltz.content.domain.enums.ContentType;
import com.ltz.content.domain.exception.AdministratorRequiredException;
import com.ltz.content.domain.exception.ContentAccessDeniedException;
import com.ltz.content.domain.exception.ContentAlreadyPublishedException;
import com.ltz.content.domain.exception.ContentNotEditableException;
import com.ltz.content.domain.exception.ContentNotFoundException;
import com.ltz.content.domain.exception.ContentNotPublishedException;
import com.ltz.content.domain.exception.OfficialAnnouncementForbiddenException;
import com.ltz.content.domain.exception.PublishRequirementsNotMetException;
import com.ltz.content.infrastructure.persistence.repository.ContentCategoryAssignmentRepository;
import com.ltz.content.infrastructure.persistence.repository.ContentImageRepository;
import com.ltz.content.infrastructure.persistence.repository.ContentRepository;
import com.ltz.content.infrastructure.persistence.repository.ContentSourceRepository;
import com.ltz.content.infrastructure.persistence.repository.ContentTranslationRepository;

/** Yayınlama (CONT-09) ve öne çıkarma (CONT-11): ön moderatör onayı yok, resmî duyuru ve öne çıkarma yalnız ADMIN. */
class ContentPublishingServiceTest {

	private static final UUID USER = UUID.fromString("0198a3c2-7b1e-7d44-9a55-3f1c2b4d5e66");
	private static final UUID OTHER = UUID.fromString("0198a3c2-8c2f-7d44-9a55-3f1c2b4d5e66");
	private static final UUID CONTENT_ID = UUID.fromString("0198a3c2-9999-7d44-9a55-3f1c2b4d5e66");
	private static final Instant NOW = Instant.parse("2026-11-07T09:00:00Z");
	private static final String LONG_BODY = "Bu gövde yayın için gereken otuz karakterden kesinlikle daha uzundur.";

	private final ContentRepository contentRepository = mock(ContentRepository.class);
	private final ContentTranslationRepository translationRepository = mock(ContentTranslationRepository.class);
	private final CurrentUserPort currentUser = mock(CurrentUserPort.class);
	private final ContentViewAssembler assembler = new ContentViewAssembler(translationRepository,
			mock(ContentImageRepository.class), mock(ContentCategoryAssignmentRepository.class),
			mock(ContentSourceRepository.class), new ContentMapper());
	private final ContentPublishingService service = new ContentPublishingService(contentRepository, translationRepository,
			new ContentAccess(contentRepository), assembler, currentUser, Clock.fixed(NOW, ZoneOffset.UTC));

	@BeforeEach
	void signIn() {
		when(currentUser.requireUserId()).thenReturn(USER);
		when(currentUser.hasAdminRole()).thenReturn(false);
	}

	private Content content(UUID author, ContentType type, ContentStatus status) {
		Content content = Content.draft(author, type, "tr", "a".repeat(64), NOW.minusSeconds(600));
		ReflectionTestUtils.setField(content, "id", CONTENT_ID);
		ReflectionTestUtils.setField(content, "status", status);
		if (status != ContentStatus.DRAFT) {
			ReflectionTestUtils.setField(content, "publishedAt", NOW.minusSeconds(300));
		}
		when(contentRepository.findByIdAndDeletedAtIsNull(CONTENT_ID)).thenReturn(Optional.of(content));
		when(contentRepository.findActiveByIdForUpdate(CONTENT_ID)).thenReturn(Optional.of(content));
		return content;
	}

	private void translations(ContentTranslation... translations) {
		when(translationRepository.findByIdContentId(CONTENT_ID)).thenReturn(List.of(translations));
	}

	private static ContentTranslation turkish(String title, String body) {
		return ContentTranslation.of(CONTENT_ID, "tr", title, null, body);
	}

	// ------------------------------------------------------------------ yayınlama

	@Test
	void anyRegisteredUserPublishesTheirOwnNewsWithoutModeratorApproval() {
		Content content = content(USER, ContentType.NEWS, ContentStatus.DRAFT);
		translations(turkish("Yeni güncelleme çıktı", LONG_BODY));

		ContentResponse response = service.publish(CONTENT_ID);

		assertThat(response.status()).isEqualTo(ContentStatus.PUBLISHED);
		assertThat(response.publishedAt()).isEqualTo(NOW);
		assertThat(content.getPublishedAt()).isEqualTo(NOW);
		verify(contentRepository).save(content);
	}

	@Test
	void userContentCanBePublishedByItsAuthorToo() {
		content(USER, ContentType.USER_CONTENT, ContentStatus.DRAFT);
		translations(turkish("Oyuncu rehberi", LONG_BODY));

		assertThat(service.publish(CONTENT_ID).status()).isEqualTo(ContentStatus.PUBLISHED);
	}

	@Test
	void normalUsersCannotPublishOfficialAnnouncements() {
		Content content = content(USER, ContentType.ANNOUNCEMENT, ContentStatus.DRAFT);
		translations(turkish("Planlı bakım duyurusu", LONG_BODY));

		assertThatThrownBy(() -> service.publish(CONTENT_ID)).isInstanceOf(OfficialAnnouncementForbiddenException.class);

		assertThat(content.getStatus()).isEqualTo(ContentStatus.DRAFT);
		verify(contentRepository, never()).save(any());
	}

	@Test
	void administratorsPublishOfficialAnnouncements() {
		when(currentUser.hasAdminRole()).thenReturn(true);
		content(USER, ContentType.ANNOUNCEMENT, ContentStatus.DRAFT);
		translations(turkish("Planlı bakım duyurusu", LONG_BODY));

		assertThat(service.publish(CONTENT_ID).status()).isEqualTo(ContentStatus.PUBLISHED);
	}

	@Test
	void publishingChecksThePublishRulesNotTheDraftRules() {
		Content content = content(USER, ContentType.NEWS, ContentStatus.DRAFT);
		translations(turkish("Kısa", "az"));

		assertThatThrownBy(() -> service.publish(CONTENT_ID))
				.isInstanceOfSatisfying(PublishRequirementsNotMetException.class, exception -> assertThat(exception.getIssues())
						.containsExactly("tr:TITLE_TOO_SHORT", "tr:BODY_TOO_SHORT"));

		assertThat(content.getStatus()).isEqualTo(ContentStatus.DRAFT);
		verify(contentRepository, never()).save(any());
	}

	@Test
	void everyTranslationMustMeetThePublishRules() {
		content(USER, ContentType.NEWS, ContentStatus.DRAFT);
		translations(turkish("Yeni güncelleme çıktı", LONG_BODY), ContentTranslation.of(CONTENT_ID, "en", "Hi", null, "x"));

		assertThatThrownBy(() -> service.publish(CONTENT_ID))
				.isInstanceOfSatisfying(PublishRequirementsNotMetException.class, exception -> assertThat(exception.getIssues())
						.containsExactly("en:TITLE_TOO_SHORT", "en:BODY_TOO_SHORT"));
	}

	@Test
	void someoneElsesDraftCannotBePublishedAndItsExistenceIsNotLeaked() {
		content(OTHER, ContentType.NEWS, ContentStatus.DRAFT);

		assertThatThrownBy(() -> service.publish(CONTENT_ID)).isInstanceOf(ContentNotFoundException.class);
		verify(contentRepository, never()).save(any());
	}

	@Test
	void someoneElsesPublishedContentIsForbidden() {
		content(OTHER, ContentType.NEWS, ContentStatus.PUBLISHED);

		assertThatThrownBy(() -> service.publish(CONTENT_ID)).isInstanceOf(ContentAccessDeniedException.class);
	}

	@Test
	void publishingTwiceIsAConflict() {
		content(USER, ContentType.NEWS, ContentStatus.PUBLISHED);

		assertThatThrownBy(() -> service.publish(CONTENT_ID)).isInstanceOf(ContentAlreadyPublishedException.class);
	}

	@Test
	void removedOrArchivedContentCannotBePublished() {
		content(USER, ContentType.NEWS, ContentStatus.REMOVED);
		assertThatThrownBy(() -> service.publish(CONTENT_ID)).isInstanceOf(ContentNotEditableException.class);

		content(USER, ContentType.NEWS, ContentStatus.ARCHIVED);
		assertThatThrownBy(() -> service.publish(CONTENT_ID)).isInstanceOf(ContentNotEditableException.class);
	}

	@Test
	void aMissingContentIsNotFound() {
		when(contentRepository.findActiveByIdForUpdate(CONTENT_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.publish(CONTENT_ID)).isInstanceOf(ContentNotFoundException.class);
	}

	// ------------------------------------------------------------------ öne çıkarma

	@Test
	void administratorsFeaturePublishedContent() {
		when(currentUser.hasAdminRole()).thenReturn(true);
		Content content = content(OTHER, ContentType.NEWS, ContentStatus.PUBLISHED);
		translations(turkish("Yeni güncelleme çıktı", LONG_BODY));

		ContentResponse response = service.feature(CONTENT_ID);

		assertThat(response.featured()).isTrue();
		assertThat(content.getFeaturedBy()).isEqualTo(USER);
		assertThat(content.getFeaturedAt()).isEqualTo(NOW);
		verify(contentRepository).save(content);
	}

	@Test
	void featuringAnAlreadyFeaturedContentChangesNothing() {
		when(currentUser.hasAdminRole()).thenReturn(true);
		Content content = content(OTHER, ContentType.NEWS, ContentStatus.PUBLISHED);
		translations(turkish("Yeni güncelleme çıktı", LONG_BODY));
		content.feature(OTHER, NOW.minusSeconds(60));

		service.feature(CONTENT_ID);

		verify(contentRepository, never()).save(any());
		assertThat(content.getFeaturedBy()).isEqualTo(OTHER);
	}

	@Test
	void onlyAdministratorsCanFeatureOrUnfeature() {
		content(USER, ContentType.NEWS, ContentStatus.PUBLISHED);

		assertThatThrownBy(() -> service.feature(CONTENT_ID)).isInstanceOf(AdministratorRequiredException.class);
		assertThatThrownBy(() -> service.unfeature(CONTENT_ID)).isInstanceOf(AdministratorRequiredException.class);
		verify(contentRepository, never()).save(any());
	}

	@Test
	void draftsCannotBeFeatured() {
		when(currentUser.hasAdminRole()).thenReturn(true);
		content(OTHER, ContentType.NEWS, ContentStatus.DRAFT);

		assertThatThrownBy(() -> service.feature(CONTENT_ID)).isInstanceOf(ContentNotPublishedException.class);
	}

	@Test
	void administratorsUnfeatureContent() {
		when(currentUser.hasAdminRole()).thenReturn(true);
		Content content = content(OTHER, ContentType.NEWS, ContentStatus.PUBLISHED);
		translations(turkish("Yeni güncelleme çıktı", LONG_BODY));
		content.feature(USER, NOW.minusSeconds(60));

		ContentResponse response = service.unfeature(CONTENT_ID);

		assertThat(response.featured()).isFalse();
		assertThat(content.getFeaturedAt()).isNull();
		verify(contentRepository).save(content);
	}

	@Test
	void featuringAMissingContentIsNotFound() {
		when(currentUser.hasAdminRole()).thenReturn(true);
		when(contentRepository.findActiveByIdForUpdate(CONTENT_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.feature(CONTENT_ID)).isInstanceOf(ContentNotFoundException.class);
	}
}
