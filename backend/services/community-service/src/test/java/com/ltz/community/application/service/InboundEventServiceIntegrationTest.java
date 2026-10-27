package com.ltz.community.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ltz.community.EventTestConfig;
import com.ltz.community.api.dto.request.InboundEventRequest;
import com.ltz.community.api.dto.response.InboundEventResult;
import com.ltz.community.api.dto.response.InboundEventResult.Outcome;
import com.ltz.community.domain.entity.Comment;
import com.ltz.community.domain.entity.Post;
import com.ltz.community.domain.entity.PostImage;
import com.ltz.community.domain.entity.Report;
import com.ltz.community.domain.enums.CommentStatus;
import com.ltz.community.domain.enums.PostStatus;
import com.ltz.community.domain.enums.ReportReason;
import com.ltz.community.domain.enums.ReportStatus;
import com.ltz.community.domain.enums.ReportTarget;
import com.ltz.community.domain.exception.InvalidInboundEventException;
import com.ltz.community.infrastructure.persistence.PostgreSqlIntegrationTest;
import com.ltz.community.infrastructure.persistence.repository.CommentRepository;
import com.ltz.community.infrastructure.persistence.repository.PostImageRepository;
import com.ltz.community.infrastructure.persistence.repository.PostRepository;
import com.ltz.community.infrastructure.persistence.repository.ReportRepository;
import com.ltz.community.infrastructure.persistence.repository.UserBlockRepository;

import jakarta.persistence.EntityManager;

/** admin-service moderasyon kararları ve social-service engelleme olayları gerçek PostgreSQL üzerinde (COM-17, COM-21). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ EventTestConfig.class, InboundEventService.class, ModerationService.class })
class InboundEventServiceIntegrationTest extends PostgreSqlIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");

	@Autowired
	private InboundEventService events;

	@Autowired
	private PostRepository postRepository;

	@Autowired
	private PostImageRepository postImageRepository;

	@Autowired
	private CommentRepository commentRepository;

	@Autowired
	private ReportRepository reportRepository;

	@Autowired
	private UserBlockRepository userBlockRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	private static InboundEventRequest event(String type, String producer, Map<String, Object> payload) {
		return new InboundEventRequest(UUID.randomUUID(), type, 1, NOW, producer, UUID.randomUUID(), payload);
	}

	private static Map<String, Object> moderation(ReportTarget target, UUID id, String action) {
		Map<String, Object> payload = new HashMap<>();
		payload.put("targetType", target.name());
		payload.put("targetId", id);
		payload.put("action", action);
		return payload;
	}

	private InboundEventResult moderate(ReportTarget target, UUID id, String action) {
		return events.handle(event("ModerationActionTaken", "admin-service", moderation(target, id, action)));
	}

	private Post visiblePost() {
		return postRepository.saveAndFlush(Post.publish(UUID.randomUUID(), "gönderi", NOW));
	}

	private Report openReport(ReportTarget target, UUID id) {
		return reportRepository.saveAndFlush(
				Report.open(target, id, UUID.randomUUID(), UUID.randomUUID(), ReportReason.SPAM, null, NOW));
	}

	private Post reload(Post post) {
		entityManager.flush();
		entityManager.clear();
		return postRepository.findById(post.getId()).orElseThrow();
	}

	@Test
	void hidingAPostHidesItAndResolvesItsOpenReports() {
		Post post = visiblePost();
		Report report = openReport(ReportTarget.POST, post.getId());

		InboundEventResult result = moderate(ReportTarget.POST, post.getId(), "HIDE");

		assertThat(result.outcome()).isEqualTo(Outcome.PROCESSED);
		assertThat(reload(post).getStatus()).isEqualTo(PostStatus.HIDDEN);
		assertThat(reportRepository.findById(report.getId()).orElseThrow().getStatus()).isEqualTo(ReportStatus.RESOLVED);
	}

	@Test
	void restoringBringsAHiddenPostBackButNeverADeletedOne() {
		Post hidden = visiblePost();
		moderate(ReportTarget.POST, hidden.getId(), "HIDE");
		moderate(ReportTarget.POST, hidden.getId(), "RESTORE");
		assertThat(reload(hidden).getStatus()).isEqualTo(PostStatus.VISIBLE);

		Post removed = visiblePost();
		moderate(ReportTarget.POST, removed.getId(), "REMOVE");
		moderate(ReportTarget.POST, removed.getId(), "RESTORE");
		Post afterRestore = reload(removed);
		assertThat(afterRestore.getStatus()).isEqualTo(PostStatus.DELETED);
		assertThat(afterRestore.getDeletedAt()).isNotNull();
	}

	@Test
	void removingAPostSoftDeletesItAndPurgesImages() {
		Post post = visiblePost();
		postImageRepository.saveAndFlush(PostImage.attach(post.getId(), new byte[] { 1 }, "image/png", 2, 2, 0, NOW));
		Report report = openReport(ReportTarget.POST, post.getId());

		moderate(ReportTarget.POST, post.getId(), "REMOVE");

		assertThat(reload(post).getStatus()).isEqualTo(PostStatus.DELETED);
		assertThat(postImageRepository.findSortOrdersByPostId(post.getId())).isEmpty();
		assertThat(reportRepository.findById(report.getId()).orElseThrow().getStatus()).isEqualTo(ReportStatus.RESOLVED);
	}

	@Test
	void dismissingOnlyClosesTheReportAndLeavesTheContent() {
		Post post = visiblePost();
		Report first = openReport(ReportTarget.POST, post.getId());
		Report second = openReport(ReportTarget.POST, post.getId());
		Map<String, Object> payload = moderation(ReportTarget.POST, post.getId(), "DISMISS");
		payload.put("reportId", first.getId());

		events.handle(event("ModerationActionTaken", "admin-service", payload));

		assertThat(reload(post).getStatus()).isEqualTo(PostStatus.VISIBLE);
		assertThat(reportRepository.findById(first.getId()).orElseThrow().getStatus()).isEqualTo(ReportStatus.DISMISSED);
		assertThat(reportRepository.findById(second.getId()).orElseThrow().getStatus()).isEqualTo(ReportStatus.OPEN);

		moderate(ReportTarget.POST, post.getId(), "DISMISS");
		assertThat(reportRepository.findById(second.getId()).orElseThrow().getStatus()).isEqualTo(ReportStatus.DISMISSED);
	}

	@Test
	void restoreLeavesReportsUntouchedAndAlreadyClosedReportsDoNotChange() {
		Post post = visiblePost();
		Report report = openReport(ReportTarget.POST, post.getId());
		moderate(ReportTarget.POST, post.getId(), "RESTORE");
		assertThat(reportRepository.findById(report.getId()).orElseThrow().getStatus()).isEqualTo(ReportStatus.OPEN);

		moderate(ReportTarget.POST, post.getId(), "HIDE");
		moderate(ReportTarget.POST, post.getId(), "DISMISS");
		assertThat(reportRepository.findById(report.getId()).orElseThrow().getStatus()).isEqualTo(ReportStatus.RESOLVED);
	}

	@Test
	void commentModerationKeepsThePostCounterInSync() {
		Post post = visiblePost();
		Comment visible = commentRepository.saveAndFlush(Comment.write(post.getId(), UUID.randomUUID(), null, "yorum", NOW));
		Comment other = commentRepository.saveAndFlush(Comment.write(post.getId(), UUID.randomUUID(), null, "diğer", NOW));
		jdbcTemplate.update("update posts set comment_count = 2 where id = ?", post.getId());
		entityManager.clear();

		moderate(ReportTarget.COMMENT, visible.getId(), "HIDE");
		assertThat(reload(post).getCommentCount()).isEqualTo(1);
		assertThat(commentRepository.findById(visible.getId()).orElseThrow().getStatus()).isEqualTo(CommentStatus.HIDDEN);

		// Yinelenen gizleme sayacı ikinci kez düşürmez.
		moderate(ReportTarget.COMMENT, visible.getId(), "HIDE");
		assertThat(reload(post).getCommentCount()).isEqualTo(1);

		moderate(ReportTarget.COMMENT, visible.getId(), "RESTORE");
		assertThat(reload(post).getCommentCount()).isEqualTo(2);
		assertThat(commentRepository.findById(visible.getId()).orElseThrow().getStatus()).isEqualTo(CommentStatus.VISIBLE);

		moderate(ReportTarget.COMMENT, other.getId(), "REMOVE");
		assertThat(reload(post).getCommentCount()).isEqualTo(1);
		moderate(ReportTarget.COMMENT, other.getId(), "REMOVE");
		moderate(ReportTarget.COMMENT, other.getId(), "RESTORE");
		assertThat(reload(post).getCommentCount()).isEqualTo(1);
		assertThat(commentRepository.findById(other.getId()).orElseThrow().getStatus()).isEqualTo(CommentStatus.DELETED);
	}

	@Test
	void removingAHiddenCommentDoesNotChangeTheCounterAgain() {
		Post post = visiblePost();
		Comment comment = commentRepository.saveAndFlush(Comment.write(post.getId(), UUID.randomUUID(), null, "yorum", NOW));
		jdbcTemplate.update("update posts set comment_count = 1 where id = ?", post.getId());
		entityManager.clear();

		moderate(ReportTarget.COMMENT, comment.getId(), "HIDE");
		moderate(ReportTarget.COMMENT, comment.getId(), "REMOVE");

		assertThat(reload(post).getCommentCount()).isZero();
		assertThat(commentRepository.findById(comment.getId()).orElseThrow().getStatus()).isEqualTo(CommentStatus.DELETED);
	}

	@Test
	void commentDismissIsHarmless() {
		Post post = visiblePost();
		Comment comment = commentRepository.saveAndFlush(Comment.write(post.getId(), UUID.randomUUID(), null, "yorum", NOW));
		Report report = openReport(ReportTarget.COMMENT, comment.getId());

		moderate(ReportTarget.COMMENT, comment.getId(), "DISMISS");

		assertThat(commentRepository.findById(comment.getId()).orElseThrow().getStatus()).isEqualTo(CommentStatus.VISIBLE);
		assertThat(reportRepository.findById(report.getId()).orElseThrow().getStatus()).isEqualTo(ReportStatus.DISMISSED);
	}

	@Test
	void unknownTargetsAreAcknowledgedWithoutEffect() {
		assertThat(moderate(ReportTarget.POST, UUID.randomUUID(), "HIDE").outcome()).isEqualTo(Outcome.PROCESSED);
		assertThat(moderate(ReportTarget.COMMENT, UUID.randomUUID(), "REMOVE").outcome()).isEqualTo(Outcome.PROCESSED);
	}

	@Test
	void theSameEventIdIsAppliedOnlyOnce() {
		Post post = visiblePost();
		Comment comment = commentRepository.saveAndFlush(Comment.write(post.getId(), UUID.randomUUID(), null, "yorum", NOW));
		jdbcTemplate.update("update posts set comment_count = 1 where id = ?", post.getId());
		entityManager.clear();
		InboundEventRequest hide = event("ModerationActionTaken", "admin-service",
				moderation(ReportTarget.COMMENT, comment.getId(), "HIDE"));

		assertThat(events.handle(hide).outcome()).isEqualTo(Outcome.PROCESSED);
		assertThat(events.handle(hide).outcome()).isEqualTo(Outcome.DUPLICATE);

		assertThat(reload(post).getCommentCount()).isZero();
		assertThat(jdbcTemplate.queryForObject("select count(*) from inbox_events where event_id = ?", Integer.class, hide.eventId()))
				.isEqualTo(1);
	}

	@Test
	void unknownEventTypesAreIgnoredWithoutAnInboxRecord() {
		InboundEventResult result = events.handle(event("SomethingElse", "whatever-service", Map.of("a", 1)));

		assertThat(result.outcome()).isEqualTo(Outcome.IGNORED);
		assertThat(jdbcTemplate.queryForObject("select count(*) from inbox_events", Integer.class)).isZero();
	}

	@Test
	void producersMustMatchTheEventType() {
		UUID id = UUID.randomUUID();
		InboundEventRequest spoofedModeration = event("ModerationActionTaken", "social-service",
				moderation(ReportTarget.POST, id, "HIDE"));
		InboundEventRequest spoofedBlock = event("UserBlocked", "admin-service",
				Map.of("blockerId", UUID.randomUUID(), "blockedId", UUID.randomUUID()));

		assertThatThrownBy(() -> events.handle(spoofedModeration)).isInstanceOf(InvalidInboundEventException.class);
		assertThatThrownBy(() -> events.handle(spoofedBlock)).isInstanceOf(InvalidInboundEventException.class);
	}

	@Test
	void invalidPayloadsAreRejectedAndNotRecordedAsProcessed() {
		UUID id = UUID.randomUUID();
		InboundEventRequest missingAction = event("ModerationActionTaken", "admin-service", Map.of("targetType", "POST", "targetId", id));
		InboundEventRequest unknownAction = event("ModerationActionTaken", "admin-service", moderation(ReportTarget.POST, id, "EXPLODE"));
		InboundEventRequest badTarget = event("ModerationActionTaken", "admin-service", Map.of("targetType", "FORUM", "targetId", id, "action", "HIDE"));
		InboundEventRequest badUuid = event("ModerationActionTaken", "admin-service", Map.of("targetType", "POST", "targetId", "degil", "action", "HIDE"));
		InboundEventRequest selfBlock = event("UserBlocked", "social-service", Map.of("blockerId", id, "blockedId", id));
		InboundEventRequest missingBlocked = event("UserUnblocked", "social-service", Map.of("blockerId", id));

		for (InboundEventRequest invalid : new InboundEventRequest[] { missingAction, unknownAction, badTarget, badUuid, selfBlock,
				missingBlocked }) {
			assertThatThrownBy(() -> events.handle(invalid)).isInstanceOf(InvalidInboundEventException.class);
		}
		assertThat(jdbcTemplate.queryForObject("select count(*) from inbox_events", Integer.class)).isZero();
	}

	@Test
	void blockAndUnblockEventsMaintainTheLocalProjection() {
		UUID blocker = UUID.randomUUID();
		UUID blocked = UUID.randomUUID();
		Map<String, Object> payload = Map.of("blockerId", blocker, "blockedId", blocked);

		assertThat(events.handle(event("UserBlocked", "social-service", payload)).outcome()).isEqualTo(Outcome.PROCESSED);
		assertThat(userBlockRepository.existsBetween(blocker, blocked)).isTrue();
		assertThat(userBlockRepository.existsBetween(blocked, blocker)).isTrue();
		// Yeni bir olay kimliğiyle gelen aynı engelleme mükerrer kayıt yaratmaz.
		events.handle(event("UserBlocked", "social-service", payload));
		assertThat(userBlockRepository.count()).isEqualTo(1);

		events.handle(event("UserUnblocked", "social-service", payload));
		assertThat(userBlockRepository.existsBetween(blocker, blocked)).isFalse();
		// Olmayan engellemeyi kaldırmak hata değildir.
		assertThat(events.handle(event("UserUnblocked", "social-service", payload)).outcome()).isEqualTo(Outcome.PROCESSED);
	}
}
