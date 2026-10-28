package com.ltz.community.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.community.api.dto.request.CreateReportRequest;
import com.ltz.community.api.dto.response.ReportResponse;
import com.ltz.community.application.port.CurrentUserPort;
import com.ltz.community.application.port.DomainEventPublisher;
import com.ltz.community.domain.entity.Comment;
import com.ltz.community.domain.entity.Report;
import com.ltz.community.domain.enums.ReportTarget;
import com.ltz.community.domain.exception.AlreadyReportedException;
import com.ltz.community.domain.exception.CommentNotFoundException;
import com.ltz.community.domain.exception.ReportingOwnContentException;
import com.ltz.community.domain.exception.TooManyReportsException;
import com.ltz.community.infrastructure.persistence.repository.CommentRepository;
import com.ltz.community.infrastructure.persistence.repository.ReportRepository;

/**
 * Gönderi ve yorum raporlama (COM-16). Rapor, içerik referansı ve gerekçeyle kaydedilir ve {@code ContentReported}
 * olayı aynı transaction'da outbox'a yazılarak admin-service moderasyon kuyruğuna aktarılır. Yalnız çağıranın görebildiği
 * (VISIBLE, engelleme/forum kurallarına uyan) içerik raporlanabilir; kendi içeriğin raporlanamaz; aynı içerik aynı kullanıcı
 * tarafından bir kez raporlanır (veritabanı UNIQUE kısıtı ile de korunur). Kullanıcı başına saatlik rapor sayısı sınırlıdır.
 */
@Service
public class ReportService {

	static final int MAX_REPORTS_PER_HOUR = 20;
	static final String EVENT_CONTENT_REPORTED = "ContentReported";
	static final String AGGREGATE_REPORT = "COMMUNITY_REPORT";

	private final ReportRepository reportRepository;
	private final CommentRepository commentRepository;
	private final PostAccess postAccess;
	private final DomainEventPublisher eventPublisher;
	private final CurrentUserPort currentUser;
	private final Clock clock;

	public ReportService(ReportRepository reportRepository, CommentRepository commentRepository, PostAccess postAccess,
			DomainEventPublisher eventPublisher, CurrentUserPort currentUser, Clock clock) {
		this.reportRepository = reportRepository;
		this.commentRepository = commentRepository;
		this.postAccess = postAccess;
		this.eventPublisher = eventPublisher;
		this.currentUser = currentUser;
		this.clock = clock;
	}

	@Transactional
	public ReportResponse reportPost(UUID postId, CreateReportRequest request) {
		UUID reporterId = currentUser.requireUserId();
		UUID authorId = postAccess.loadViewable(postId, reporterId).getAuthorId();
		return file(ReportTarget.POST, postId, authorId, reporterId, request);
	}

	@Transactional
	public ReportResponse reportComment(UUID commentId, CreateReportRequest request) {
		UUID reporterId = currentUser.requireUserId();
		Comment comment = commentRepository.findById(commentId).filter(Comment::isVisible)
				.orElseThrow(CommentNotFoundException::new);
		// Yorumun gönderisi ve yazarı çağıran için görünür olmalıdır (silinmiş/gizli gönderi, gizli forum, engelleme).
		postAccess.loadViewable(comment.getPostId(), reporterId);
		postAccess.requireNotBlocked(reporterId, comment.getAuthorId(), CommentNotFoundException::new);
		return file(ReportTarget.COMMENT, commentId, comment.getAuthorId(), reporterId, request);
	}

	private ReportResponse file(ReportTarget targetType, UUID targetId, UUID authorId, UUID reporterId,
			CreateReportRequest request) {
		if (authorId.equals(reporterId)) {
			throw new ReportingOwnContentException();
		}
		Instant now = Instant.now(clock);
		if (reportRepository.countByReporterIdAndCreatedAtAfter(reporterId, now.minus(Duration.ofHours(1))) >= MAX_REPORTS_PER_HOUR) {
			throw new TooManyReportsException();
		}
		if (reportRepository.existsByTargetTypeAndTargetIdAndReporterId(targetType, targetId, reporterId)) {
			throw new AlreadyReportedException();
		}
		String detail = request.detail() == null || request.detail().isBlank() ? null : request.detail().strip();
		Report saved;
		try {
			saved = reportRepository.saveAndFlush(
					Report.open(targetType, targetId, authorId, reporterId, request.reason(), detail, now));
		} catch (DataIntegrityViolationException _) {
			throw new AlreadyReportedException();
		}
		eventPublisher.publish(AGGREGATE_REPORT, saved.getId(), EVENT_CONTENT_REPORTED, payload(saved));
		return new ReportResponse(saved.getId(), saved.getTargetType(), saved.getTargetId(), saved.getReason(),
				saved.getStatus(), saved.getCreatedAt());
	}

	/** Moderasyon için gereken en az bilgi: referanslar, gerekçe ve isteğe bağlı açıklama (içerik metni taşınmaz). */
	private static Map<String, Object> payload(Report report) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("reportId", report.getId());
		payload.put("targetType", report.getTargetType().name());
		payload.put("targetId", report.getTargetId());
		payload.put("targetAuthorId", report.getTargetAuthorId());
		payload.put("reporterId", report.getReporterId());
		payload.put("reason", report.getReason().name());
		payload.put("detail", report.getDetail());
		payload.put("reportedAt", report.getCreatedAt().toString());
		return payload;
	}
}
