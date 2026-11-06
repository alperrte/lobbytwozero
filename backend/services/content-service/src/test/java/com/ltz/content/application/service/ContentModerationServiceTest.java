package com.ltz.content.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.ltz.content.api.dto.response.ModerationActionResponse;
import com.ltz.content.application.port.CurrentUserPort;
import com.ltz.content.domain.entity.Content;
import com.ltz.content.domain.entity.ContentModerationAction;
import com.ltz.content.domain.enums.ContentStatus;
import com.ltz.content.domain.enums.ContentType;
import com.ltz.content.domain.enums.ModerationActionType;
import com.ltz.content.domain.enums.ModerationSource;
import com.ltz.content.domain.enums.ModeratorRole;
import com.ltz.content.domain.exception.ContentNotFoundException;
import com.ltz.content.domain.exception.InsufficientModerationAuthorityException;
import com.ltz.content.domain.exception.InvalidContentRequestException;
import com.ltz.content.domain.exception.ModeratorRequiredException;
import com.ltz.content.infrastructure.persistence.repository.ContentModerationActionRepository;
import com.ltz.content.infrastructure.persistence.repository.ContentRepository;

/**
 * CONT-10: admin-service kararının uygulanması. Karar yetkisi içerik türüne göre denetlenir (resmî duyuru yalnız ADMIN),
 * yetersiz karar hiçbir şeyi değiştirmez, geçersiz durum geçişi etkisizdir ve her karar değişmez kayda yazılır.
 */
class ContentModerationServiceTest {

	private static final UUID AUTHOR = UUID.fromString("0198a3c2-7b1e-7d44-9a55-3f1c2b4d5e66");
	private static final UUID MODERATOR = UUID.fromString("0198a3c2-8c2f-7d44-9a55-3f1c2b4d5e66");
	private static final UUID CONTENT_ID = UUID.fromString("0198a3c2-9999-7d44-9a55-3f1c2b4d5e66");
	private static final UUID EVENT_ID = UUID.fromString("0198a3c2-eeee-7d44-9a55-3f1c2b4d5e66");
	private static final UUID REPORT_ID = UUID.fromString("0198a3c2-dddd-7d44-9a55-3f1c2b4d5e66");
	private static final Instant NOW = Instant.parse("2026-11-07T10:00:00Z");

	private final ContentRepository contentRepository = mock(ContentRepository.class);
	private final ContentModerationActionRepository actionRepository = mock(ContentModerationActionRepository.class);
	private final CurrentUserPort currentUser = mock(CurrentUserPort.class);
	private final ContentModerationService service = new ContentModerationService(contentRepository, actionRepository,
			currentUser, Clock.fixed(NOW, ZoneOffset.UTC));

	@BeforeEach
	void stubStorage() {
		when(contentRepository.save(any(Content.class))).then(returnsFirstArg());
		when(actionRepository.save(any(ContentModerationAction.class))).then(returnsFirstArg());
	}

	private Content content(ContentType type, ContentStatus status) {
		Content content = Content.draft(AUTHOR, type, "tr", "a".repeat(64), NOW.minusSeconds(7200));
		ReflectionTestUtils.setField(content, "id", CONTENT_ID);
		ReflectionTestUtils.setField(content, "status", status);
		if (status != ContentStatus.DRAFT) {
			ReflectionTestUtils.setField(content, "publishedAt", NOW.minusSeconds(3600));
		}
		if (status == ContentStatus.REMOVED) {
			ReflectionTestUtils.setField(content, "removedAt", NOW.minusSeconds(60));
			ReflectionTestUtils.setField(content, "removedBy", MODERATOR);
			ReflectionTestUtils.setField(content, "removedReason", "Eski gerekçe");
		}
		when(contentRepository.findActiveByIdForUpdate(CONTENT_ID)).thenReturn(Optional.of(content));
		when(contentRepository.findByIdAndDeletedAtIsNull(CONTENT_ID)).thenReturn(Optional.of(content));
		return content;
	}

	private boolean decide(ModerationActionType action, ModeratorRole role, String reason) {
		return service.applyDecision(CONTENT_ID, action, MODERATOR, role, reason, REPORT_ID, EVENT_ID);
	}

	private void asHistoryReader() {
		when(currentUser.requireUserId()).thenReturn(MODERATOR);
		when(currentUser.hasModeratorRole()).thenReturn(true);
	}

	// ------------------------------------------------------------------ yayından kaldırma

	@Test
	void aModeratorDecisionRemovesPublishedNewsAndTheDecisionIsRecorded() {
		Content content = content(ContentType.NEWS, ContentStatus.PUBLISHED);
		content.feature(UUID.randomUUID(), NOW.minusSeconds(1800));

		boolean applied = decide(ModerationActionType.REMOVE, ModeratorRole.MODERATOR, "  Spam içerik  ");

		assertThat(applied).isTrue();
		assertThat(content.getStatus()).isEqualTo(ContentStatus.REMOVED);
		assertThat(content.getRemovedBy()).isEqualTo(MODERATOR);
		assertThat(content.getRemovedReason()).isEqualTo("Spam içerik");
		assertThat(content.getRemovedAt()).isEqualTo(NOW);
		assertThat(content.isFeatured()).isFalse();
		assertThat(content.getFeaturedBy()).isNull();
		ArgumentCaptor<ContentModerationAction> saved = ArgumentCaptor.forClass(ContentModerationAction.class);
		verify(actionRepository).save(saved.capture());
		ContentModerationAction entry = saved.getValue();
		assertThat(entry.getContentId()).isEqualTo(CONTENT_ID);
		assertThat(entry.getAction()).isEqualTo(ModerationActionType.REMOVE);
		assertThat(entry.getActorId()).isEqualTo(MODERATOR);
		assertThat(entry.getActorRole()).isEqualTo(ModeratorRole.MODERATOR);
		assertThat(entry.getSource()).isEqualTo(ModerationSource.ADMIN_SERVICE);
		assertThat(entry.getReason()).isEqualTo("Spam içerik");
		assertThat(entry.getReportId()).isEqualTo(REPORT_ID);
		assertThat(entry.getEventId()).isEqualTo(EVENT_ID);
		assertThat(entry.getCreatedAt()).isEqualTo(NOW);
		verify(contentRepository).save(content);
		verifyNoInteractions(currentUser);
	}

	@Test
	void anAdminDecisionAlsoRemovesNormalContent() {
		Content content = content(ContentType.USER_CONTENT, ContentStatus.PUBLISHED);

		assertThat(decide(ModerationActionType.REMOVE, ModeratorRole.ADMIN, "Kural ihlali")).isTrue();

		assertThat(content.getStatus()).isEqualTo(ContentStatus.REMOVED);
	}

	@Test
	void officialAnnouncementsAreRemovedAndRestoredOnlyByAnAdminDecision() {
		Content announcement = content(ContentType.ANNOUNCEMENT, ContentStatus.PUBLISHED);

		assertThatThrownBy(() -> decide(ModerationActionType.REMOVE, ModeratorRole.MODERATOR, "Neden"))
				.isInstanceOf(InsufficientModerationAuthorityException.class);
		assertThat(announcement.getStatus()).isEqualTo(ContentStatus.PUBLISHED);

		assertThat(decide(ModerationActionType.REMOVE, ModeratorRole.ADMIN, "Hatalı duyuru")).isTrue();
		assertThat(announcement.getStatus()).isEqualTo(ContentStatus.REMOVED);

		assertThatThrownBy(() -> decide(ModerationActionType.RESTORE, ModeratorRole.MODERATOR, "Neden"))
				.isInstanceOf(InsufficientModerationAuthorityException.class);
		assertThat(announcement.getStatus()).isEqualTo(ContentStatus.REMOVED);

		assertThat(decide(ModerationActionType.RESTORE, ModeratorRole.ADMIN, "Düzeltildi")).isTrue();
		assertThat(announcement.getStatus()).isEqualTo(ContentStatus.PUBLISHED);
	}

	@Test
	void insufficientAuthorityWritesNothingEvenWhenTheStateWouldNotChange() {
		content(ContentType.ANNOUNCEMENT, ContentStatus.DRAFT);

		assertThatThrownBy(() -> decide(ModerationActionType.REMOVE, ModeratorRole.MODERATOR, "Neden"))
				.isInstanceOf(InsufficientModerationAuthorityException.class);

		verify(contentRepository, never()).save(any());
		verify(actionRepository, never()).save(any());
	}

	@Test
	void aMissingRoleIsNeverEnough() {
		content(ContentType.NEWS, ContentStatus.PUBLISHED);

		assertThatThrownBy(() -> decide(ModerationActionType.REMOVE, null, "Neden"))
				.isInstanceOf(InsufficientModerationAuthorityException.class);

		verify(actionRepository, never()).save(any());
	}

	@Test
	void aBlankOrOversizedReasonIsRejectedAndNothingChanges() {
		Content content = content(ContentType.NEWS, ContentStatus.PUBLISHED);
		String oversized = "a".repeat(501);

		assertThatThrownBy(() -> decide(ModerationActionType.REMOVE, ModeratorRole.MODERATOR, "   "))
				.isInstanceOf(InvalidContentRequestException.class);
		assertThatThrownBy(() -> decide(ModerationActionType.REMOVE, ModeratorRole.MODERATOR, oversized))
				.isInstanceOf(InvalidContentRequestException.class);

		assertThat(content.getStatus()).isEqualTo(ContentStatus.PUBLISHED);
		verify(actionRepository, never()).save(any());
	}

	// ------------------------------------------------------------------ geri alma

	@Test
	void restoringBringsTheContentBackAndClearsTheRemovalButKeepsThePublishTime() {
		Content content = content(ContentType.NEWS, ContentStatus.REMOVED);

		assertThat(decide(ModerationActionType.RESTORE, ModeratorRole.MODERATOR, "Yanlış karar")).isTrue();

		assertThat(content.getStatus()).isEqualTo(ContentStatus.PUBLISHED);
		assertThat(content.getRemovedAt()).isNull();
		assertThat(content.getRemovedBy()).isNull();
		assertThat(content.getRemovedReason()).isNull();
		assertThat(content.isFeatured()).isFalse();
		assertThat(content.getPublishedAt()).isEqualTo(NOW.minusSeconds(3600));
		ArgumentCaptor<ContentModerationAction> saved = ArgumentCaptor.forClass(ContentModerationAction.class);
		verify(actionRepository).save(saved.capture());
		assertThat(saved.getValue().getAction()).isEqualTo(ModerationActionType.RESTORE);
		assertThat(saved.getValue().getReason()).isEqualTo("Yanlış karar");
	}

	// ------------------------------------------------------------------ idempotans (olay yeniden teslimi)

	@Test
	void aDecisionThatAlreadyHoldsIsANoOpWithoutAuditRecord() {
		content(ContentType.NEWS, ContentStatus.REMOVED);
		assertThat(decide(ModerationActionType.REMOVE, ModeratorRole.MODERATOR, "Neden")).isFalse();

		content(ContentType.NEWS, ContentStatus.PUBLISHED);
		assertThat(decide(ModerationActionType.RESTORE, ModeratorRole.MODERATOR, "Neden")).isFalse();

		content(ContentType.NEWS, ContentStatus.DRAFT);
		assertThat(decide(ModerationActionType.REMOVE, ModeratorRole.MODERATOR, "Neden")).isFalse();

		verify(contentRepository, never()).save(any());
		verify(actionRepository, never()).save(any());
	}

	@Test
	void aDecisionForMissingOrDeletedContentIsANoOp() {
		when(contentRepository.findActiveByIdForUpdate(CONTENT_ID)).thenReturn(Optional.empty());

		assertThat(decide(ModerationActionType.REMOVE, ModeratorRole.ADMIN, "Neden")).isFalse();

		verify(actionRepository, never()).save(any());
	}

	// ------------------------------------------------------------------ geçmiş (salt okunur)

	@Test
	void historyListsTheRecordsForModeratorsAndAdministrators() {
		asHistoryReader();
		content(ContentType.NEWS, ContentStatus.REMOVED);
		ContentModerationAction removal = ContentModerationAction.of(CONTENT_ID, ModerationActionType.REMOVE, MODERATOR,
				ModeratorRole.MODERATOR, "Spam", NOW.minusSeconds(60)).viaEvent(REPORT_ID, EVENT_ID);
		when(actionRepository.findByContentIdOrderByCreatedAtDescIdDesc(CONTENT_ID)).thenReturn(List.of(removal));

		List<ModerationActionResponse> history = service.history(CONTENT_ID);

		assertThat(history).singleElement().satisfies(item -> {
			assertThat(item.contentId()).isEqualTo(CONTENT_ID);
			assertThat(item.action()).isEqualTo(ModerationActionType.REMOVE);
			assertThat(item.actorId()).isEqualTo(MODERATOR);
			assertThat(item.actorRole()).isEqualTo(ModeratorRole.MODERATOR);
			assertThat(item.source()).isEqualTo(ModerationSource.ADMIN_SERVICE);
			assertThat(item.reportId()).isEqualTo(REPORT_ID);
			assertThat(item.resultingStatus()).isEqualTo(ContentStatus.REMOVED);
			assertThat(item.reason()).isEqualTo("Spam");
		});
	}

	@Test
	void anAdministratorMayReadTheHistoryToo() {
		when(currentUser.requireUserId()).thenReturn(MODERATOR);
		when(currentUser.hasAdminRole()).thenReturn(true);
		content(ContentType.NEWS, ContentStatus.PUBLISHED);
		when(actionRepository.findByContentIdOrderByCreatedAtDescIdDesc(CONTENT_ID)).thenReturn(List.of());

		assertThat(service.history(CONTENT_ID)).isEmpty();
	}

	@Test
	void historyIsForbiddenForEveryoneElseBeforeTheContentIsLoaded() {
		when(currentUser.requireUserId()).thenReturn(AUTHOR);
		content(ContentType.NEWS, ContentStatus.REMOVED);

		assertThatThrownBy(() -> service.history(CONTENT_ID)).isInstanceOf(ModeratorRequiredException.class);

		verify(contentRepository, never()).findByIdAndDeletedAtIsNull(any());
		verify(actionRepository, never()).findByContentIdOrderByCreatedAtDescIdDesc(any());
	}

	@Test
	void historyOfMissingOrDeletedContentIsNotFound() {
		asHistoryReader();
		when(contentRepository.findByIdAndDeletedAtIsNull(CONTENT_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.history(CONTENT_ID)).isInstanceOf(ContentNotFoundException.class);
	}
}
