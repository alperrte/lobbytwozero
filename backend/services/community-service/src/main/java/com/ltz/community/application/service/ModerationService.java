package com.ltz.community.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.community.domain.entity.Comment;
import com.ltz.community.domain.entity.Post;
import com.ltz.community.domain.entity.Report;
import com.ltz.community.domain.enums.CommentStatus;
import com.ltz.community.domain.enums.ModerationAction;
import com.ltz.community.domain.enums.PostStatus;
import com.ltz.community.domain.enums.ReportStatus;
import com.ltz.community.domain.enums.ReportTarget;
import com.ltz.community.infrastructure.persistence.repository.CommentRepository;
import com.ltz.community.infrastructure.persistence.repository.PostImageRepository;
import com.ltz.community.infrastructure.persistence.repository.PostRepository;
import com.ltz.community.infrastructure.persistence.repository.ReportRepository;

/**
 * admin-service'in verdiği moderasyon kararlarını topluluk içeriğine uygular (COM-17). Kararın kendisi (kim, neden)
 * admin-service'te denetim kaydıyla tutulur; burada yalnız içerik durumu değişir. Uygulama idempotenttir: durumu zaten
 * hedefteki içerik değişmez, silinmiş içerik geri getirilmez, bulunamayan içerik sessizce yok sayılır (olay yeniden
 * denenmesin). İlgili açık raporlar karara göre sonuçlandırılır: HIDE/REMOVE → RESOLVED, DISMISS → DISMISSED.
 */
@Service
public class ModerationService {

	private static final Logger log = LoggerFactory.getLogger(ModerationService.class);

	private final PostRepository postRepository;
	private final PostImageRepository postImageRepository;
	private final CommentRepository commentRepository;
	private final ReportRepository reportRepository;
	private final Clock clock;

	public ModerationService(PostRepository postRepository, PostImageRepository postImageRepository,
			CommentRepository commentRepository, ReportRepository reportRepository, Clock clock) {
		this.postRepository = postRepository;
		this.postImageRepository = postImageRepository;
		this.commentRepository = commentRepository;
		this.reportRepository = reportRepository;
		this.clock = clock;
	}

	/** Çağıranın transaction'ına katılır (olayın inbox kaydıyla birlikte işlenir). */
	@Transactional(propagation = Propagation.MANDATORY)
	public void apply(ReportTarget targetType, UUID targetId, ModerationAction action, UUID reportId) {
		Instant now = Instant.now(clock);
		boolean found = switch (targetType) {
			case POST -> applyToPost(targetId, action, now);
			case COMMENT -> applyToComment(targetId, action, now);
		};
		if (!found) {
			log.info("Moderation action {} ignored: {} {} not found", action, targetType, targetId);
			return;
		}
		closeReports(targetType, targetId, action, reportId, now);
	}

	private boolean applyToPost(UUID postId, ModerationAction action, Instant now) {
		Post post = postRepository.findByIdForUpdate(postId).orElse(null);
		if (post == null) {
			return false;
		}
		switch (action) {
			case HIDE -> post.hide(now);
			case RESTORE -> post.restore(now);
			case REMOVE -> removePost(post, now);
			case DISMISS -> {
				// Rapor reddi içeriği değiştirmez.
			}
		}
		postRepository.save(post);
		return true;
	}

	private void removePost(Post post, Instant now) {
		if (post.getStatus() != PostStatus.DELETED) {
			post.markDeleted(now);
			postImageRepository.deleteByPostId(post.getId());
		}
	}

	private boolean applyToComment(UUID commentId, ModerationAction action, Instant now) {
		Comment comment = commentRepository.findById(commentId).orElse(null);
		if (comment == null) {
			return false;
		}
		switch (action) {
			case HIDE -> hideComment(comment, now);
			case RESTORE -> restoreComment(comment, now);
			case REMOVE -> removeComment(comment, now);
			case DISMISS -> {
				// Rapor reddi içeriği değiştirmez.
			}
		}
		commentRepository.save(comment);
		return true;
	}

	private void hideComment(Comment comment, Instant now) {
		if (comment.hide(now)) {
			postRepository.decrementCommentCount(comment.getPostId());
		}
	}

	private void restoreComment(Comment comment, Instant now) {
		if (comment.restore(now)) {
			postRepository.restoreCommentCount(comment.getPostId());
		}
	}

	/** Sayaç yalnız herkese açık (sayılan) yorum kaldırılırsa düşer; gizli yorum zaten sayılmıyordu. */
	private void removeComment(Comment comment, Instant now) {
		CommentStatus before = comment.getStatus();
		if (before == CommentStatus.DELETED) {
			return;
		}
		comment.markDeleted(now);
		if (before == CommentStatus.VISIBLE) {
			postRepository.decrementCommentCount(comment.getPostId());
		}
	}

	private void closeReports(ReportTarget targetType, UUID targetId, ModerationAction action, UUID reportId, Instant now) {
		ReportStatus outcome = switch (action) {
			case HIDE, REMOVE -> ReportStatus.RESOLVED;
			case DISMISS -> ReportStatus.DISMISSED;
			case RESTORE -> null;
		};
		if (outcome == null) {
			return;
		}
		List<Report> open = reportRepository.findByTargetTypeAndTargetIdAndStatus(targetType, targetId, ReportStatus.OPEN);
		for (Report report : open) {
			if ((reportId == null || report.getId().equals(reportId)) && report.close(outcome, now)) {
				reportRepository.save(report);
			}
		}
	}
}
