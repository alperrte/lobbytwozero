package com.ltz.content.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.content.TestImages;
import com.ltz.content.api.dto.request.ContentFeedRequest;
import com.ltz.content.api.dto.request.CreateContentRequest;
import com.ltz.content.api.dto.request.InboundEventRequest;
import com.ltz.content.api.dto.request.UpdateContentRequest;
import com.ltz.content.api.dto.response.ContentResponse;
import com.ltz.content.api.dto.response.InboundEventResult.Outcome;
import com.ltz.content.api.dto.response.ModerationActionResponse;
import com.ltz.content.application.mapper.ContentMapper;
import com.ltz.content.application.port.CurrentUserPort;
import com.ltz.content.application.port.GameCatalogPort;
import com.ltz.content.domain.enums.ContentStatus;
import com.ltz.content.domain.enums.ContentType;
import com.ltz.content.domain.enums.ModerationActionType;
import com.ltz.content.domain.enums.ModerationSource;
import com.ltz.content.domain.enums.ModeratorRole;
import com.ltz.content.domain.exception.ContentNotEditableException;
import com.ltz.content.domain.exception.ContentNotFoundException;
import com.ltz.content.domain.exception.InsufficientModerationAuthorityException;
import com.ltz.content.domain.exception.InvalidInboundEventException;
import com.ltz.content.domain.exception.ModeratorRequiredException;
import com.ltz.content.infrastructure.config.ClockConfig;
import com.ltz.content.infrastructure.messaging.inbox.InboxEventStore;
import com.ltz.content.infrastructure.persistence.JdbcContentFeed;
import com.ltz.content.infrastructure.persistence.PostgreSqlIntegrationTest;

import jakarta.persistence.EntityManager;

/**
 * CONT-10 gerçek PostgreSQL üzerinde. Moderasyon kaynaklı durum değişikliği yalnız admin-service'in
 * {@code ModerationActionTaken} olayıyla uygulanır: kaldırma içeriği akıştan, detaydan ve görsel adresinden anında çıkarır;
 * yazar nedenini görür ama düzenleyemez; resmî duyuru yalnız ADMIN kararıyla değişir, yetersiz karar hiçbir iz bırakmaz
 * (içerik, geçmiş ve inbox); başarılı olayın yeniden teslimi ikinci kayıt yaratmaz. Yalnız oturum ve game-service sahtedir.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ ContentService.class, ContentImageService.class, ImageSanitizer.class, ContentPublishingService.class,
		ContentMetadataService.class, ContentTranslationService.class, ContentModerationService.class, InboundEventService.class,
		InboxEventStore.class, ContentFeedService.class, JdbcContentFeed.class, ContentAccess.class, ContentMapper.class,
		ContentFingerprint.class, ContentViewAssembler.class, ClockConfig.class,
		ContentModerationIntegrationTest.JdbcClientConfig.class })
class ContentModerationIntegrationTest extends PostgreSqlIntegrationTest {

	@TestConfiguration
	static class JdbcClientConfig {
		@Bean
		JdbcClient jdbcClient(JdbcTemplate jdbcTemplate) {
			return JdbcClient.create(jdbcTemplate);
		}
	}

	private static final UUID AUTHOR = UUID.fromString("0198a3c2-7b1e-7d44-9a55-3f1c2b4d5e66");
	private static final UUID MODERATOR = UUID.fromString("0198a3c2-8c2f-7d44-9a55-3f1c2b4d5e66");
	private static final UUID ADMIN = UUID.fromString("0198a3c2-9d30-7d44-9a55-3f1c2b4d5e66");
	private static final String BODY = "Bu gövde metni yayın için gereken otuz karakterden açıkça daha uzundur.";

	@Autowired
	private ContentService contentService;

	@Autowired
	private ContentImageService imageService;

	@Autowired
	private ContentPublishingService publishingService;

	@Autowired
	private ContentModerationService moderationService;

	@Autowired
	private InboundEventService inboundEventService;

	@Autowired
	private ContentFeedService feedService;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private EntityManager entityManager;

	@MockitoBean
	private CurrentUserPort currentUser;

	@MockitoBean
	private GameCatalogPort gameCatalog;

	private enum Role { PLAYER, MODERATOR, ADMIN }

	@BeforeEach
	void signInAsTheAuthor() {
		as(AUTHOR, Role.PLAYER);
	}

	private void as(UUID userId, Role role) {
		when(currentUser.requireUserId()).thenReturn(userId);
		when(currentUser.findUserId()).thenReturn(Optional.of(userId));
		when(currentUser.hasAdminRole()).thenReturn(role == Role.ADMIN);
		when(currentUser.hasModeratorRole()).thenReturn(role == Role.MODERATOR);
		when(currentUser.hasContentCreatorRole()).thenReturn(false);
	}

	private void asGuest() {
		when(currentUser.findUserId()).thenReturn(Optional.empty());
		when(currentUser.hasAdminRole()).thenReturn(false);
		when(currentUser.hasModeratorRole()).thenReturn(false);
		when(currentUser.hasContentCreatorRole()).thenReturn(false);
	}

	private UUID published(ContentType type, String title) {
		UUID id = contentService.createDraft(new CreateContentRequest(type, null, title, "Kısa özet", BODY)).id();
		publishingService.publish(id);
		return id;
	}

	private int feedSize() {
		entityManager.flush();
		entityManager.clear();
		return feedService.list(new ContentFeedRequest(null, null, null, null, null, null, null, null, null, null)).items().size();
	}

	private static InboundEventRequest decision(UUID eventId, UUID contentId, String action, String role, String reason) {
		return new InboundEventRequest(eventId, "ModerationActionTaken", 1, Instant.parse("2026-11-07T10:00:00Z"), "admin-service",
				contentId, Map.of("targetType", "CONTENT", "targetId", contentId.toString(), "action", action, "moderatorId",
						MODERATOR.toString(), "moderatorRole", role, "reason", reason));
	}

	private Outcome remove(UUID contentId, String role, String reason) {
		return inboundEventService.handle(decision(UUID.randomUUID(), contentId, "REMOVE", role, reason)).outcome();
	}

	private Outcome restore(UUID contentId, String role, String reason) {
		return inboundEventService.handle(decision(UUID.randomUUID(), contentId, "RESTORE", role, reason)).outcome();
	}

	private int actionCount(UUID contentId) {
		return jdbc.queryForObject("select count(*) from content_moderation_actions where content_id = ?", Integer.class, contentId);
	}

	// ------------------------------------------------------------------ admin-service kararı

	@Test
	void aModeratorDecisionTakesNewsOffTheFeedDetailAndImageUrlAtOnce() throws IOException {
		UUID id = published(ContentType.NEWS, "Kaldırılacak haber");
		UUID imageId = imageService.addImage(id, TestImages.png(10, 10)).id();
		assertThat(feedSize()).isEqualTo(1);
		asGuest();
		assertThat(imageService.getImage(id, imageId).content()).isNotEmpty();

		assertThat(remove(id, "MODERATOR", "  Kurallara aykırı içerik  ")).isEqualTo(Outcome.PROCESSED);

		assertThat(feedSize()).isZero();
		asGuest();
		assertThatThrownBy(() -> contentService.getContent(id, null)).isInstanceOf(ContentNotFoundException.class);
		assertThatThrownBy(() -> imageService.getImage(id, imageId)).isInstanceOf(ContentNotFoundException.class);
		assertThat(jdbc.queryForObject("select removed_by from contents where id = ?", UUID.class, id)).isEqualTo(MODERATOR);
		assertThat(jdbc.queryForObject("select removed_reason from contents where id = ?", String.class, id))
				.isEqualTo("Kurallara aykırı içerik");
		assertThat(jdbc.queryForObject("select actor_role from content_moderation_actions where content_id = ?", String.class, id))
				.isEqualTo("MODERATOR");
		assertThat(actionCount(id)).isEqualTo(1);
	}

	@Test
	void theAuthorSeesTheReasonButCannotEditOrDeleteRemovedContent() throws IOException {
		UUID id = published(ContentType.NEWS, "Yazarın haberi");
		remove(id, "MODERATOR", "Telif ihlali");
		entityManager.flush();
		entityManager.clear();

		as(AUTHOR, Role.PLAYER);
		ContentResponse seen = contentService.getContent(id, null);
		assertThat(seen.status()).isEqualTo(ContentStatus.REMOVED);
		assertThat(seen.removalReason()).isEqualTo("Telif ihlali");
		assertThat(seen.removedAt()).isNotNull();
		UpdateContentRequest update = new UpdateContentRequest("Yeni başlık", null, null);
		byte[] image = TestImages.png(10, 10);

		assertThatThrownBy(() -> contentService.updateContent(id, update)).isInstanceOf(ContentNotEditableException.class);
		assertThatThrownBy(() -> contentService.deleteContent(id)).isInstanceOf(ContentNotEditableException.class);
		assertThatThrownBy(() -> imageService.addImage(id, image)).isInstanceOf(ContentNotEditableException.class);
		assertThatThrownBy(() -> publishingService.publish(id)).isInstanceOf(ContentNotEditableException.class);
	}

	@Test
	void restoreBringsTheContentBackAndTheHistoryKeepsBothDecisionsNewestFirst() {
		UUID id = published(ContentType.NEWS, "Geri alınacak haber");
		remove(id, "MODERATOR", "Yanlış anlaşılma");
		assertThat(restore(id, "ADMIN", "İtiraz haklı bulundu")).isEqualTo(Outcome.PROCESSED);

		assertThat(feedSize()).isEqualTo(1);
		asGuest();
		ContentResponse visible = contentService.getContent(id, null);
		assertThat(visible.status()).isEqualTo(ContentStatus.PUBLISHED);
		assertThat(visible.removalReason()).isNull();
		assertThat(jdbc.queryForObject("select removed_at from contents where id = ?", Object.class, id)).isNull();

		as(MODERATOR, Role.MODERATOR);
		List<ModerationActionResponse> history = moderationService.history(id);
		assertThat(history).extracting(ModerationActionResponse::action)
				.containsExactly(ModerationActionType.RESTORE, ModerationActionType.REMOVE);
		assertThat(history).extracting(ModerationActionResponse::actorRole).containsExactly(ModeratorRole.ADMIN, ModeratorRole.MODERATOR);
		assertThat(history).extracting(ModerationActionResponse::source).containsOnly(ModerationSource.ADMIN_SERVICE);
		assertThat(history).extracting(ModerationActionResponse::reason).containsExactly("İtiraz haklı bulundu", "Yanlış anlaşılma");
	}

	@Test
	void removingEndsTheFeaturedStatus() {
		UUID id = published(ContentType.NEWS, "Öne çıkan haber");
		as(ADMIN, Role.ADMIN);
		publishingService.feature(id);
		entityManager.flush();
		assertThat(jdbc.queryForObject("select featured from contents where id = ?", Boolean.class, id)).isTrue();

		remove(id, "ADMIN", "Hatalı bilgi");
		entityManager.flush();
		assertThat(jdbc.queryForObject("select featured from contents where id = ?", Boolean.class, id)).isFalse();

		restore(id, "ADMIN", "Düzeltildi");
		entityManager.flush();
		assertThat(jdbc.queryForObject("select featured from contents where id = ?", Boolean.class, id)).isFalse();
	}

	// ------------------------------------------------------------------ resmî duyuru yetkisi (olay tüketicisi)

	@Test
	void anAnnouncementIsRemovedAndRestoredOnlyByAnAdminDecision() {
		as(ADMIN, Role.ADMIN);
		UUID id = published(ContentType.ANNOUNCEMENT, "Sunucu bakımı");

		assertThatThrownBy(() -> remove(id, "MODERATOR", "Neden")).isInstanceOf(InsufficientModerationAuthorityException.class);
		assertThat(feedSize()).isEqualTo(1);
		assertThat(actionCount(id)).isZero();

		assertThat(remove(id, "ADMIN", "Hatalı duyuru")).isEqualTo(Outcome.PROCESSED);
		assertThat(feedSize()).isZero();

		assertThatThrownBy(() -> restore(id, "MODERATOR", "Neden")).isInstanceOf(InsufficientModerationAuthorityException.class);
		assertThat(feedSize()).isZero();
		assertThat(actionCount(id)).isEqualTo(1);

		assertThat(restore(id, "ADMIN", "Düzeltildi")).isEqualTo(Outcome.PROCESSED);
		assertThat(feedSize()).isEqualTo(1);
		assertThat(actionCount(id)).isEqualTo(2);
	}

	@Test
	void normalNewsAcceptBothModeratorAndAdminDecisions() {
		UUID first = published(ContentType.NEWS, "Birinci haber");
		UUID second = published(ContentType.USER_CONTENT, "İkinci içerik");

		assertThat(remove(first, "MODERATOR", "Neden")).isEqualTo(Outcome.PROCESSED);
		assertThat(remove(second, "ADMIN", "Neden")).isEqualTo(Outcome.PROCESSED);

		assertThat(feedSize()).isZero();
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void aRejectedDecisionLeavesNoTraceAndTheSameEventCanBeRetriedWithSufficientAuthority() {
		as(ADMIN, Role.ADMIN);
		UUID id = published(ContentType.ANNOUNCEMENT, "Reddedilecek karar");
		UUID eventId = UUID.randomUUID();
		InboundEventRequest insufficient = decision(eventId, id, "REMOVE", "MODERATOR", "Yetersiz yetki");
		try {
			assertThatThrownBy(() -> inboundEventService.handle(insufficient))
					.isInstanceOf(InsufficientModerationAuthorityException.class);

			// İşlem geri alındı: içerik, geçmiş ve inbox değişmedi; olay "işlendi" diye kaydedilmedi.
			assertThat(jdbc.queryForObject("select status from contents where id = ?", String.class, id)).isEqualTo("PUBLISHED");
			assertThat(actionCount(id)).isZero();
			assertThat(jdbc.queryForObject("select count(*) from inbox_events where event_id = ?", Integer.class, eventId)).isZero();

			assertThat(inboundEventService.handle(decision(eventId, id, "REMOVE", "ADMIN", "Yetkili karar")).outcome())
					.isEqualTo(Outcome.PROCESSED);
			assertThat(jdbc.queryForObject("select status from contents where id = ?", String.class, id)).isEqualTo("REMOVED");
			assertThat(actionCount(id)).isEqualTo(1);
			assertThat(jdbc.queryForObject("select count(*) from inbox_events where event_id = ?", Integer.class, eventId)).isEqualTo(1);
		} finally {
			// Bu test işlemsizdir (gerçek commit); diğer testleri etkilememesi için kendi verisini siler.
			jdbc.update("delete from contents where id = ?", id);
			jdbc.update("delete from inbox_events where event_id = ?", eventId);
		}
	}

	// ------------------------------------------------------------------ idempotans ve geçersiz olay

	@Test
	void aSuccessfulEventRedeliveredCreatesNoSecondChangeOrAuditRecord() {
		UUID id = published(ContentType.NEWS, "Olayla kaldırılacak");
		UUID eventId = UUID.randomUUID();

		assertThat(inboundEventService.handle(decision(eventId, id, "REMOVE", "MODERATOR", "Rapor sonucu")).outcome())
				.isEqualTo(Outcome.PROCESSED);
		assertThat(inboundEventService.handle(decision(eventId, id, "REMOVE", "MODERATOR", "Rapor sonucu")).outcome())
				.isEqualTo(Outcome.DUPLICATE);

		assertThat(feedSize()).isZero();
		assertThat(actionCount(id)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from inbox_events where event_id = ?", Integer.class, eventId)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select source from content_moderation_actions where event_id = ?", String.class, eventId))
				.isEqualTo(ModerationSource.ADMIN_SERVICE.name());
	}

	@Test
	void aNewEventForAnAlreadyRemovedOrMissingContentIsProcessedWithoutAnotherAuditRecord() {
		UUID id = published(ContentType.NEWS, "Olay idempotansı");

		assertThat(remove(UUID.randomUUID(), "ADMIN", "Yok")).isEqualTo(Outcome.PROCESSED);
		remove(id, "MODERATOR", "İlk karar");
		remove(id, "MODERATOR", "İkinci karar");

		assertThat(actionCount(id)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select removed_reason from contents where id = ?", String.class, id)).isEqualTo("İlk karar");
	}

	@Test
	void dismissAndInvalidEventsLeaveTheContentAndTheMissingRoleIsNeverRecorded() {
		UUID id = published(ContentType.NEWS, "Reddedilen rapor");
		UUID dismissId = UUID.randomUUID();
		Map<String, Object> dismissPayload = Map.of("targetType", "CONTENT", "targetId", id.toString(), "action", "DISMISS");

		assertThat(inboundEventService.handle(new InboundEventRequest(dismissId, "ModerationActionTaken", 1,
				Instant.parse("2026-11-07T10:00:00Z"), "admin-service", id, dismissPayload)).outcome()).isEqualTo(Outcome.PROCESSED);
		assertThat(feedSize()).isEqualTo(1);

		UUID badId = UUID.randomUUID();
		Map<String, Object> noRole = Map.of("targetType", "CONTENT", "targetId", id.toString(), "action", "REMOVE", "moderatorId",
				MODERATOR.toString(), "reason", "Rolsüz karar");
		InboundEventRequest missingRole = new InboundEventRequest(badId, "ModerationActionTaken", 1,
				Instant.parse("2026-11-07T10:00:00Z"), "admin-service", id, noRole);
		assertThatThrownBy(() -> inboundEventService.handle(missingRole)).isInstanceOf(InvalidInboundEventException.class);

		assertThat(feedSize()).isEqualTo(1);
		assertThat(actionCount(id)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from inbox_events where event_id = ?", Integer.class, badId)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from inbox_events where event_id = ?", Integer.class, dismissId)).isEqualTo(1);
	}

	// ------------------------------------------------------------------ geçmiş (salt okunur) yetkisi

	@Test
	void onlyModeratorsAndAdministratorsCanReadTheHistory() {
		UUID id = published(ContentType.NEWS, "Geçmiş okuma");
		remove(id, "MODERATOR", "Neden");

		as(AUTHOR, Role.PLAYER);
		assertThatThrownBy(() -> moderationService.history(id)).isInstanceOf(ModeratorRequiredException.class);
		as(MODERATOR, Role.MODERATOR);
		assertThat(moderationService.history(id)).hasSize(1);
		as(ADMIN, Role.ADMIN);
		assertThat(moderationService.history(id)).hasSize(1);
	}

	@Test
	void theAuthorCanStillDeleteTheirOwnPublishedNewsThroughTheNormalEndpoint() {
		UUID id = published(ContentType.NEWS, "Yazarın silebildiği haber");

		contentService.deleteContent(id);

		assertThat(feedSize()).isZero();
		asGuest();
		assertThatThrownBy(() -> contentService.getContent(id, null)).isInstanceOf(ContentNotFoundException.class);
	}
}
