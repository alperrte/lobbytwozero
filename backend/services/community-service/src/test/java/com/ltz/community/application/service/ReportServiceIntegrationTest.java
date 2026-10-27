package com.ltz.community.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import com.ltz.community.EventTestConfig;
import com.ltz.community.api.dto.request.CreateReportRequest;
import com.ltz.community.api.dto.response.ReportResponse;
import com.ltz.community.application.port.CurrentUserPort;
import com.ltz.community.domain.entity.Comment;
import com.ltz.community.domain.entity.Post;
import com.ltz.community.domain.entity.Report;
import com.ltz.community.domain.enums.PostStatus;
import com.ltz.community.domain.enums.ReportReason;
import com.ltz.community.domain.enums.ReportStatus;
import com.ltz.community.domain.enums.ReportTarget;
import com.ltz.community.domain.exception.AlreadyReportedException;
import com.ltz.community.domain.exception.CommentNotFoundException;
import com.ltz.community.domain.exception.PostNotFoundException;
import com.ltz.community.domain.exception.ReportingOwnContentException;
import com.ltz.community.domain.exception.TooManyReportsException;
import com.ltz.community.infrastructure.persistence.PostgreSqlIntegrationTest;
import com.ltz.community.infrastructure.persistence.repository.CommentRepository;
import com.ltz.community.infrastructure.persistence.repository.PostRepository;
import com.ltz.community.infrastructure.persistence.repository.ReportRepository;
import com.ltz.community.infrastructure.persistence.repository.UserBlockRepository;

import jakarta.persistence.EntityManager;

/** Gönderi/yorum raporlamayı ve outbox'a yazılan ContentReported olayını gerçek PostgreSQL üzerinde doğrular (COM-16). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ EventTestConfig.class, ReportService.class, PostAccess.class })
class ReportServiceIntegrationTest extends PostgreSqlIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");
	private static final CreateReportRequest SPAM_REPORT = new CreateReportRequest(ReportReason.SPAM, null);
	private static final CreateReportRequest VIOLENCE_REPORT = new CreateReportRequest(ReportReason.VIOLENCE, null);

	@MockitoBean
	private CurrentUserPort currentUser;

	@Autowired
	private ReportService reports;

	@Autowired
	private PostRepository postRepository;

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

	private UUID reporter;
	private UUID author;

	@BeforeEach
	void setUp() {
		reporter = UUID.randomUUID();
		author = UUID.randomUUID();
		when(currentUser.requireUserId()).thenReturn(reporter);
	}

	private Post post(PostStatus status) {
		Post post = Post.publish(author, "uygunsuz gönderi", NOW);
		if (status != PostStatus.VISIBLE) {
			ReflectionTestUtils.setField(post, "status", status);
			if (status == PostStatus.DELETED) {
				ReflectionTestUtils.setField(post, "deletedAt", NOW);
			}
		}
		return postRepository.saveAndFlush(post);
	}

	private Comment comment(Post post) {
		return commentRepository.saveAndFlush(Comment.write(post.getId(), author, null, "uygunsuz yorum", NOW));
	}

	private static CreateReportRequest request(ReportReason reason, String detail) {
		return new CreateReportRequest(reason, detail);
	}

	private List<Map<String, Object>> outbox(String eventType) {
		return jdbcTemplate.queryForList(
				"select aggregate_type, aggregate_id, status, cast(payload as text) as payload from outbox_events where event_type = ?",
				eventType);
	}

	private void reportPost(UUID postId, CreateReportRequest request) {
		reports.reportPost(postId, request);
	}

	@Test
	void reportingAPostStoresTheReferenceAndQueuesAContentReportedEvent() {
		Post reported = post(PostStatus.VISIBLE);

		ReportResponse response = reports.reportPost(reported.getId(), request(ReportReason.SPAM, "  reklam  "));

		assertThat(response.targetType()).isEqualTo(ReportTarget.POST);
		assertThat(response.targetId()).isEqualTo(reported.getId());
		assertThat(response.reason()).isEqualTo(ReportReason.SPAM);
		assertThat(response.status()).isEqualTo(ReportStatus.OPEN);
		Report stored = reportRepository.findById(response.id()).orElseThrow();
		assertThat(stored.getReporterId()).isEqualTo(reporter);
		assertThat(stored.getTargetAuthorId()).isEqualTo(author);
		assertThat(stored.getDetail()).isEqualTo("reklam");

		List<Map<String, Object>> events = outbox("ContentReported");
		assertThat(events).hasSize(1);
		assertThat(events.getFirst()).containsEntry("aggregate_type", "COMMUNITY_REPORT")
				.containsEntry("aggregate_id", response.id()).containsEntry("status", "PENDING");
		String payload = (String) events.getFirst().get("payload");
		// İçerik metni olaya taşınmaz (kişisel veri minimumu).
		assertThat(payload).contains(reported.getId().toString(), reporter.toString(), author.toString(), "SPAM")
				.doesNotContain("uygunsuz gönderi");
	}

	@Test
	void reportingACommentUsesTheCommentAsTarget() {
		Comment reported = comment(post(PostStatus.VISIBLE));

		ReportResponse response = reports.reportComment(reported.getId(), request(ReportReason.HARASSMENT, null));

		assertThat(response.targetType()).isEqualTo(ReportTarget.COMMENT);
		assertThat(reportRepository.findById(response.id()).orElseThrow().getDetail()).isNull();
		assertThat(outbox("ContentReported")).hasSize(1);
	}

	@Test
	void blankDetailIsStoredAsNull() {
		Post reported = post(PostStatus.VISIBLE);

		ReportResponse response = reports.reportPost(reported.getId(), request(ReportReason.OTHER, "   "));

		assertThat(reportRepository.findById(response.id()).orElseThrow().getDetail()).isNull();
	}

	@Test
	void ownContentCannotBeReported() {
		Post own = postRepository.saveAndFlush(Post.publish(reporter, "benim", NOW));
		Comment ownComment = commentRepository.saveAndFlush(Comment.write(own.getId(), reporter, null, "benim yorumum", NOW));
		UUID postId = own.getId();
		UUID commentId = ownComment.getId();

		assertThatThrownBy(() -> reportPost(postId, SPAM_REPORT))
				.isInstanceOf(ReportingOwnContentException.class);
		assertThatThrownBy(() -> reports.reportComment(commentId, SPAM_REPORT))
				.isInstanceOf(ReportingOwnContentException.class);
		assertThat(reportRepository.count()).isZero();
		assertThat(outbox("ContentReported")).isEmpty();
	}

	@Test
	void sameUserReportsTheSameContentOnlyOnce() {
		Post reported = post(PostStatus.VISIBLE);
		UUID postId = reported.getId();
		reports.reportPost(postId, SPAM_REPORT);

		assertThatThrownBy(() -> reportPost(postId, VIOLENCE_REPORT))
				.isInstanceOf(AlreadyReportedException.class);
		assertThat(reportRepository.count()).isEqualTo(1);
		assertThat(outbox("ContentReported")).hasSize(1);
	}

	@Test
	void differentUsersCanReportTheSameContent() {
		Post reported = post(PostStatus.VISIBLE);
		reports.reportPost(reported.getId(), SPAM_REPORT);
		when(currentUser.requireUserId()).thenReturn(UUID.randomUUID());

		reports.reportPost(reported.getId(), SPAM_REPORT);

		assertThat(reportRepository.count()).isEqualTo(2);
	}

	@Test
	void inaccessibleContentCannotBeReported() {
		Post hidden = post(PostStatus.HIDDEN);
		Post deleted = post(PostStatus.DELETED);
		UUID hiddenId = hidden.getId();
		UUID deletedId = deleted.getId();
		UUID unknownId = UUID.randomUUID();

		assertThatThrownBy(() -> reportPost(hiddenId, SPAM_REPORT)).isInstanceOf(PostNotFoundException.class);
		assertThatThrownBy(() -> reportPost(deletedId, SPAM_REPORT)).isInstanceOf(PostNotFoundException.class);
		assertThatThrownBy(() -> reportPost(unknownId, SPAM_REPORT)).isInstanceOf(PostNotFoundException.class);
		assertThatThrownBy(() -> reports.reportComment(unknownId, SPAM_REPORT))
				.isInstanceOf(CommentNotFoundException.class);
	}

	@Test
	void commentOnAnInaccessiblePostOrDeletedCommentCannotBeReported() {
		Post visible = post(PostStatus.VISIBLE);
		Comment removed = comment(visible);
		removed.markDeleted(NOW);
		commentRepository.saveAndFlush(removed);
		Comment onHiddenPost = comment(visible);
		jdbcTemplate.update("update posts set status = 'HIDDEN' where id = ?", visible.getId());
		entityManager.clear();
		UUID removedId = removed.getId();
		UUID onHiddenPostId = onHiddenPost.getId();

		assertThatThrownBy(() -> reports.reportComment(removedId, SPAM_REPORT))
				.isInstanceOf(CommentNotFoundException.class);
		assertThatThrownBy(() -> reports.reportComment(onHiddenPostId, SPAM_REPORT))
				.isInstanceOf(PostNotFoundException.class);
	}

	@Test
	void blockedAuthorsContentCannotBeReportedEitherWay() {
		Post reported = post(PostStatus.VISIBLE);
		Comment reportedComment = comment(reported);
		userBlockRepository.insertIfAbsent(author, reporter, NOW);
		UUID postId = reported.getId();
		UUID commentId = reportedComment.getId();

		assertThatThrownBy(() -> reportPost(postId, SPAM_REPORT)).isInstanceOf(PostNotFoundException.class);
		assertThatThrownBy(() -> reports.reportComment(commentId, SPAM_REPORT))
				.isInstanceOf(PostNotFoundException.class);
	}

	@Test
	void reportsPerUserPerHourAreLimited() {
		for (int i = 0; i < ReportService.MAX_REPORTS_PER_HOUR; i++) {
			reports.reportPost(post(PostStatus.VISIBLE).getId(), SPAM_REPORT);
		}
		UUID extra = post(PostStatus.VISIBLE).getId();

		assertThatThrownBy(() -> reportPost(extra, SPAM_REPORT)).isInstanceOf(TooManyReportsException.class);
		assertThat(reportRepository.count()).isEqualTo(ReportService.MAX_REPORTS_PER_HOUR);
	}

	@Test
	void entityClosesOnlyOpenReportsOnce() {
		Report report = Report.open(ReportTarget.POST, UUID.randomUUID(), author, reporter, ReportReason.SPAM, null, NOW);

		assertThat(report.close(ReportStatus.OPEN, NOW)).isFalse();
		assertThat(report.close(ReportStatus.RESOLVED, NOW.plusSeconds(1))).isTrue();
		assertThat(report.close(ReportStatus.DISMISSED, NOW.plusSeconds(2))).isFalse();
		assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);
		assertThat(report.getUpdatedAt()).isEqualTo(NOW.plusSeconds(1));
	}
}
