package com.ltz.content.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.ltz.content.api.dto.request.CreateContentRequest;
import com.ltz.content.api.dto.request.UpdateContentRequest;
import com.ltz.content.api.dto.response.ContentResponse;
import com.ltz.content.application.mapper.ContentMapper;
import com.ltz.content.application.port.CurrentUserPort;
import com.ltz.content.domain.entity.Content;
import com.ltz.content.domain.entity.ContentTranslation;
import com.ltz.content.domain.entity.ContentTranslationId;
import com.ltz.content.domain.enums.ContentStatus;
import com.ltz.content.domain.enums.ContentType;
import com.ltz.content.domain.exception.ContentAccessDeniedException;
import com.ltz.content.domain.exception.ContentNotEditableException;
import com.ltz.content.domain.exception.ContentNotFoundException;
import com.ltz.content.domain.exception.DraftLimitExceededException;
import com.ltz.content.domain.exception.DuplicateContentException;
import com.ltz.content.domain.exception.InvalidContentRequestException;
import com.ltz.content.domain.exception.OfficialAnnouncementForbiddenException;
import com.ltz.content.domain.exception.TooManyContentsException;
import com.ltz.content.infrastructure.persistence.repository.ContentImageRepository;
import com.ltz.content.infrastructure.persistence.repository.ContentRepository;
import com.ltz.content.infrastructure.persistence.repository.ContentTranslationRepository;

class ContentServiceTest {

	private static final UUID USER = UUID.fromString("0198a3c2-7b1e-7d44-9a55-3f1c2b4d5e66");
	private static final UUID OTHER = UUID.fromString("0198a3c2-8c2f-7d44-9a55-3f1c2b4d5e66");
	private static final UUID CONTENT_ID = UUID.fromString("0198a3c2-9999-7d44-9a55-3f1c2b4d5e66");
	private static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");
	private static final String OLD_HASH = "f".repeat(64);

	private final ContentRepository contentRepository = mock(ContentRepository.class);
	private final ContentTranslationRepository translationRepository = mock(ContentTranslationRepository.class);
	private final ContentImageRepository imageRepository = mock(ContentImageRepository.class);
	private final CurrentUserPort currentUser = mock(CurrentUserPort.class);
	private final ContentService service = new ContentService(contentRepository, translationRepository, imageRepository,
			new ContentAccess(contentRepository), new ContentMapper(), new ContentFingerprint(), currentUser,
			Clock.fixed(NOW, ZoneOffset.UTC));

	@BeforeEach
	void stubStorage() {
		when(currentUser.requireUserId()).thenReturn(USER);
		when(contentRepository.saveAndFlush(any(Content.class))).thenAnswer(invocation -> {
			Content content = invocation.getArgument(0);
			if (content.getId() == null) {
				ReflectionTestUtils.setField(content, "id", CONTENT_ID);
			}
			return content;
		});
		when(contentRepository.save(any(Content.class))).then(returnsFirstArg());
		when(translationRepository.save(any(ContentTranslation.class))).then(returnsFirstArg());
	}

	private static CreateContentRequest create(ContentType type, String title, String summary, String body) {
		return new CreateContentRequest(type, null, title, summary, body);
	}

	private Content existing(UUID author, ContentStatus status) {
		Content content = Content.draft(author, ContentType.NEWS, "tr", OLD_HASH, NOW.minusSeconds(3600));
		ReflectionTestUtils.setField(content, "id", CONTENT_ID);
		ReflectionTestUtils.setField(content, "status", status);
		when(contentRepository.findByIdAndDeletedAtIsNull(CONTENT_ID)).thenReturn(Optional.of(content));
		when(contentRepository.findActiveByIdForUpdate(CONTENT_ID)).thenReturn(Optional.of(content));
		return content;
	}

	private ContentTranslation existingTranslation() {
		ContentTranslation translation = ContentTranslation.of(CONTENT_ID, "tr", "Eski başlık", "Eski özet", "Eski gövde");
		when(translationRepository.findById(new ContentTranslationId(CONTENT_ID, "tr"))).thenReturn(Optional.of(translation));
		return translation;
	}

	// ------------------------------------------------------------------ oluşturma (CONT-05, CONT-08)

	@Test
	void registeredUserCreatesADraftOwnedByTheSessionUser() {
		ContentResponse response = service.createDraft(create(ContentType.NEWS, "  Yeni Güncelleme  ", "  ", null));

		ArgumentCaptor<Content> saved = ArgumentCaptor.forClass(Content.class);
		verify(contentRepository).saveAndFlush(saved.capture());
		assertThat(saved.getValue().getAuthorId()).isEqualTo(USER);
		assertThat(saved.getValue().getStatus()).isEqualTo(ContentStatus.DRAFT);
		assertThat(saved.getValue().getDefaultLocale()).isEqualTo("tr");
		assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
		assertThat(saved.getValue().getContentHash()).matches("[0-9a-f]{64}");

		ArgumentCaptor<ContentTranslation> translation = ArgumentCaptor.forClass(ContentTranslation.class);
		verify(translationRepository).save(translation.capture());
		assertThat(translation.getValue().getTitle()).isEqualTo("Yeni Güncelleme");
		assertThat(translation.getValue().getSummary()).isNull();
		assertThat(translation.getValue().getBody()).isNull();

		assertThat(response.id()).isEqualTo(CONTENT_ID);
		assertThat(response.authorId()).isEqualTo(USER);
		assertThat(response.status()).isEqualTo(ContentStatus.DRAFT);
		assertThat(response.locale()).isEqualTo("tr");
		assertThat(response.title()).isEqualTo("Yeni Güncelleme");
		assertThat(response.images()).isEmpty();
	}

	@Test
	void explicitLocaleIsKept() {
		ContentResponse response = service.createDraft(new CreateContentRequest(ContentType.USER_CONTENT, "en",
				"Patch notes", "Short", "Body"));

		assertThat(response.locale()).isEqualTo("en");
		assertThat(response.type()).isEqualTo(ContentType.USER_CONTENT);
	}

	@Test
	void normalUsersCannotCreateOfficialAnnouncements() {
		when(currentUser.hasAdminRole()).thenReturn(false);

		CreateContentRequest request = create(ContentType.ANNOUNCEMENT, "Resmi duyuru", null, null);
		assertThatThrownBy(() -> service.createDraft(request))
				.isInstanceOf(OfficialAnnouncementForbiddenException.class);

		verify(contentRepository, never()).saveAndFlush(any());
		verify(translationRepository, never()).save(any());
	}

	@Test
	void administratorsCanCreateOfficialAnnouncements() {
		when(currentUser.hasAdminRole()).thenReturn(true);

		ContentResponse response = service.createDraft(create(ContentType.ANNOUNCEMENT, "Bakım duyurusu", null, null));

		assertThat(response.type()).isEqualTo(ContentType.ANNOUNCEMENT);
		assertThat(response.status()).isEqualTo(ContentStatus.DRAFT);
	}

	@Test
	void newsAndUserContentDoNotNeedAnyExtraRole() {
		when(currentUser.hasAdminRole()).thenReturn(false);

		assertThat(service.createDraft(create(ContentType.NEWS, "Haber", null, null)).type()).isEqualTo(ContentType.NEWS);
		assertThat(service.createDraft(create(ContentType.USER_CONTENT, "Rehber", null, null)).type())
				.isEqualTo(ContentType.USER_CONTENT);
	}

	@Test
	void creationRateLimitIsEnforcedPerAuthor() {
		when(contentRepository.countByAuthorIdAndCreatedAtAfter(eq(USER), any(Instant.class)))
				.thenReturn((long) ContentService.MAX_CREATIONS_PER_HOUR);

		CreateContentRequest request = create(ContentType.NEWS, "Haber", null, null);
		assertThatThrownBy(() -> service.createDraft(request))
				.isInstanceOf(TooManyContentsException.class);

		ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
		verify(contentRepository).countByAuthorIdAndCreatedAtAfter(eq(USER), since.capture());
		assertThat(since.getValue()).isEqualTo(NOW.minusSeconds(3600));
		verify(contentRepository, never()).saveAndFlush(any());
	}

	@Test
	void activeDraftLimitIsEnforcedPerAuthor() {
		when(contentRepository.countByAuthorIdAndStatusAndDeletedAtIsNull(USER, ContentStatus.DRAFT))
				.thenReturn((long) ContentService.MAX_ACTIVE_DRAFTS);

		CreateContentRequest request = create(ContentType.NEWS, "Haber", null, null);
		assertThatThrownBy(() -> service.createDraft(request))
				.isInstanceOf(DraftLimitExceededException.class);

		verify(contentRepository, never()).saveAndFlush(any());
	}

	@Test
	void sameTextFromTheSameAuthorIsADuplicate() {
		when(contentRepository.existsByAuthorIdAndTypeAndContentHashAndDeletedAtIsNull(eq(USER), eq(ContentType.NEWS),
				anyString())).thenReturn(true);

		CreateContentRequest request = create(ContentType.NEWS, "Haber", "Özet", "Gövde");
		assertThatThrownBy(() -> service.createDraft(request))
				.isInstanceOf(DuplicateContentException.class);

		verify(contentRepository, never()).saveAndFlush(any());
	}

	@Test
	void aConcurrentDuplicateCaughtByTheUniqueIndexBecomesADuplicateError() {
		when(contentRepository.saveAndFlush(any(Content.class))).thenThrow(new DataIntegrityViolationException(
				"could not execute statement",
				new RuntimeException("ERROR: duplicate key value violates unique constraint \"uq_contents_author_id_type_content_hash\"")));

		CreateContentRequest request = create(ContentType.NEWS, "Haber", null, null);
		assertThatThrownBy(() -> service.createDraft(request))
				.isInstanceOf(DuplicateContentException.class)
				.hasCauseInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void otherIntegrityViolationsAreNotDisguisedAsDuplicates() {
		DataIntegrityViolationException failure = new DataIntegrityViolationException("violates check constraint \"ck_contents_type\"");
		when(contentRepository.saveAndFlush(any(Content.class))).thenThrow(failure);

		CreateContentRequest request = create(ContentType.NEWS, "Haber", null, null);
		assertThatThrownBy(() -> service.createDraft(request)).isSameAs(failure);
	}

	// ------------------------------------------------------------------ düzenleme (CONT-06, CONT-08)

	@Test
	void ownerEditsOnlyTheProvidedFields() {
		Content content = existing(USER, ContentStatus.DRAFT);
		ContentTranslation translation = existingTranslation();

		ContentResponse response = service.updateDraft(CONTENT_ID, new UpdateContentRequest(null, "  Yeni özet ", null));

		assertThat(translation.getTitle()).isEqualTo("Eski başlık");
		assertThat(translation.getSummary()).isEqualTo("Yeni özet");
		assertThat(translation.getBody()).isEqualTo("Eski gövde");
		assertThat(content.getUpdatedAt()).isEqualTo(NOW);
		assertThat(content.getContentHash()).isNotEqualTo(OLD_HASH).matches("[0-9a-f]{64}");
		verify(contentRepository).saveAndFlush(content);
		assertThat(response.summary()).isEqualTo("Yeni özet");
	}

	@Test
	void editingNeverTouchesTheAuthorTypeOrStatus() {
		Content content = existing(USER, ContentStatus.DRAFT);
		existingTranslation();

		service.updateDraft(CONTENT_ID, new UpdateContentRequest("Başka başlık", null, null));

		assertThat(content.getAuthorId()).isEqualTo(USER);
		assertThat(content.getType()).isEqualTo(ContentType.NEWS);
		assertThat(content.getStatus()).isEqualTo(ContentStatus.DRAFT);
		assertThat(content.getDefaultLocale()).isEqualTo("tr");
	}

	@Test
	void blankSummaryOrBodyClearsTheField() {
		existing(USER, ContentStatus.DRAFT);
		ContentTranslation translation = existingTranslation();

		service.updateDraft(CONTENT_ID, new UpdateContentRequest(null, "   ", ""));

		assertThat(translation.getSummary()).isNull();
		assertThat(translation.getBody()).isNull();
		assertThat(translation.getTitle()).isEqualTo("Eski başlık");
	}

	@Test
	void anUpdateWithoutAnyFieldIsRejected() {
		existing(USER, ContentStatus.DRAFT);
		existingTranslation();

		UpdateContentRequest request = new UpdateContentRequest(null, null, null);
		assertThatThrownBy(() -> service.updateDraft(CONTENT_ID, request))
				.isInstanceOf(InvalidContentRequestException.class);
	}

	@Test
	void theTitleCannotBeBlanked() {
		existing(USER, ContentStatus.DRAFT);
		existingTranslation();

		UpdateContentRequest request = new UpdateContentRequest("   ", null, null);
		assertThatThrownBy(() -> service.updateDraft(CONTENT_ID, request))
				.isInstanceOf(InvalidContentRequestException.class);
		verify(contentRepository, never()).saveAndFlush(any());
	}

	@Test
	void anUnchangedUpdateWritesNothing() {
		existing(USER, ContentStatus.DRAFT);
		existingTranslation();

		ContentResponse response = service.updateDraft(CONTENT_ID, new UpdateContentRequest("Eski başlık", "Eski özet", null));

		verify(contentRepository, never()).saveAndFlush(any());
		verify(translationRepository, never()).save(any());
		assertThat(response.title()).isEqualTo("Eski başlık");
	}

	@Test
	void editingIntoAnExistingDuplicateIsRejected() {
		existing(USER, ContentStatus.DRAFT);
		existingTranslation();
		when(contentRepository.existsByAuthorIdAndTypeAndContentHashAndDeletedAtIsNullAndIdNot(eq(USER),
				eq(ContentType.NEWS), anyString(), eq(CONTENT_ID))).thenReturn(true);

		UpdateContentRequest request = new UpdateContentRequest("Başka içeriğin başlığı", null, null);
		assertThatThrownBy(() -> service.updateDraft(CONTENT_ID, request))
				.isInstanceOf(DuplicateContentException.class);
		verify(contentRepository, never()).saveAndFlush(any());
	}

	@Test
	void someoneElsesDraftLooksNonExistentWhenEdited() {
		existing(OTHER, ContentStatus.DRAFT);

		UpdateContentRequest request = new UpdateContentRequest("Hack", null, null);
		assertThatThrownBy(() -> service.updateDraft(CONTENT_ID, request))
				.isInstanceOf(ContentNotFoundException.class);
		verify(contentRepository, never()).saveAndFlush(any());
	}

	@Test
	void someoneElsesPublishedContentIsForbiddenToEdit() {
		existing(OTHER, ContentStatus.PUBLISHED);

		UpdateContentRequest request = new UpdateContentRequest("Hack", null, null);
		assertThatThrownBy(() -> service.updateDraft(CONTENT_ID, request))
				.isInstanceOf(ContentAccessDeniedException.class);
	}

	@Test
	void onlyDraftsCanBeEditedByTheirOwner() {
		for (ContentStatus status : new ContentStatus[] { ContentStatus.PUBLISHED, ContentStatus.REMOVED, ContentStatus.ARCHIVED }) {
			existing(USER, status);

			UpdateContentRequest request = new UpdateContentRequest("Yeni", null, null);
			assertThatThrownBy(() -> service.updateDraft(CONTENT_ID, request))
					.isInstanceOf(ContentNotEditableException.class);
		}
	}

	@Test
	void editingAMissingContentIsNotFound() {
		when(contentRepository.findByIdAndDeletedAtIsNull(CONTENT_ID)).thenReturn(Optional.empty());

		UpdateContentRequest request = new UpdateContentRequest("Yeni", null, null);
		assertThatThrownBy(() -> service.updateDraft(CONTENT_ID, request))
				.isInstanceOf(ContentNotFoundException.class);
	}

	// ------------------------------------------------------------------ silme (CONT-07)

	@Test
	void ownerSoftDeletesTheRowAndPermanentlyErasesItsTextAndImages() {
		Content content = existing(USER, ContentStatus.DRAFT);

		service.deleteContent(CONTENT_ID);

		assertThat(content.isDeleted()).isTrue();
		assertThat(content.getDeletedAt()).isEqualTo(NOW);
		// Metinden türeyen parmak izi de nötr değerle değiştirilir (saklama süresi 0 gün).
		assertThat(content.getContentHash()).isEqualTo(Content.ERASED_HASH);
		verify(contentRepository).save(content);
		verify(imageRepository).deleteByContentId(CONTENT_ID);
		verify(translationRepository).deleteByContentId(CONTENT_ID);
	}

	@Test
	void deletionDoesNotResetTheHourlyCreationQuota() {
		existing(USER, ContentStatus.DRAFT);
		when(contentRepository.countByAuthorIdAndCreatedAtAfter(eq(USER), any(Instant.class)))
				.thenReturn((long) ContentService.MAX_CREATIONS_PER_HOUR);

		service.deleteContent(CONTENT_ID);

		// Silme, oluşturma sayımına dahil edilen satırı kaldırmaz; ikinci bir içerik hâlâ sınıra takılır.
		CreateContentRequest request = create(ContentType.NEWS, "Haber", null, null);
		assertThatThrownBy(() -> service.createDraft(request))
				.isInstanceOf(TooManyContentsException.class);
		verify(contentRepository, never()).delete(any(Content.class));
		verify(contentRepository, never()).deleteById(any());
	}

	@Test
	void someoneElsesContentCannotBeDeletedAndItsExistenceIsNotLeaked() {
		existing(OTHER, ContentStatus.DRAFT);

		assertThatThrownBy(() -> service.deleteContent(CONTENT_ID)).isInstanceOf(ContentNotFoundException.class);

		verify(contentRepository, never()).save(any());
		verify(imageRepository, never()).deleteByContentId(any());
		verify(translationRepository, never()).deleteByContentId(any());
	}

	@Test
	void someoneElsesPublishedContentIsForbiddenToDelete() {
		existing(OTHER, ContentStatus.PUBLISHED);

		assertThatThrownBy(() -> service.deleteContent(CONTENT_ID)).isInstanceOf(ContentAccessDeniedException.class);
	}

	@Test
	void moderatorRemovedContentIsKeptAsAModerationRecord() {
		Content content = existing(USER, ContentStatus.REMOVED);

		assertThatThrownBy(() -> service.deleteContent(CONTENT_ID)).isInstanceOf(ContentNotEditableException.class);

		assertThat(content.isDeleted()).isFalse();
		verify(imageRepository, never()).deleteByContentId(any());
		verify(translationRepository, never()).deleteByContentId(any());
	}

	@Test
	void deletingAnAlreadyDeletedOrMissingContentIsNotFound() {
		when(contentRepository.findActiveByIdForUpdate(CONTENT_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.deleteContent(CONTENT_ID)).isInstanceOf(ContentNotFoundException.class);
	}

	// ------------------------------------------------------------------ okuma

	@Test
	void ownerReadsOwnDraftWithTheImageList() {
		existing(USER, ContentStatus.DRAFT);
		existingTranslation();

		ContentResponse response = service.getContent(CONTENT_ID);

		assertThat(response.title()).isEqualTo("Eski başlık");
		assertThat(response.status()).isEqualTo(ContentStatus.DRAFT);
		verify(imageRepository).findByContentIdOrderBySortOrderAsc(CONTENT_ID);
	}

	@Test
	void aDraftOfAnotherUserIsNotVisible() {
		existing(OTHER, ContentStatus.DRAFT);

		assertThatThrownBy(() -> service.getContent(CONTENT_ID)).isInstanceOf(ContentNotFoundException.class);
	}

	@Test
	void publishedContentOfAnotherUserIsVisible() {
		existing(OTHER, ContentStatus.PUBLISHED);
		existingTranslation();

		assertThat(service.getContent(CONTENT_ID).status()).isEqualTo(ContentStatus.PUBLISHED);
	}

	@Test
	void aContentWithoutTranslationIsAnInternalError() {
		existing(USER, ContentStatus.DRAFT);
		when(translationRepository.findById(any(ContentTranslationId.class))).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getContent(CONTENT_ID)).isInstanceOf(IllegalStateException.class);
	}
}
