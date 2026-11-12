package com.ltz.review.application.service;

import com.ltz.review.api.dto.response.AdminReportResponse;
import com.ltz.review.api.dto.response.AdminReportedReviewPageResponse;
import com.ltz.review.api.dto.response.AdminReviewResponse;
import com.ltz.review.domain.entity.Review;
import com.ltz.review.domain.enums.ModerationAction;
import com.ltz.review.domain.enums.ReportOutcome;
import com.ltz.review.domain.enums.ReportStatus;
import com.ltz.review.domain.exception.ReviewException;
import com.ltz.review.infrastructure.persistence.repository.AccountWriteLock;
import com.ltz.review.infrastructure.persistence.repository.ModerationRepository;
import com.ltz.review.infrastructure.persistence.repository.ReviewRepository;
import com.ltz.review.infrastructure.persistence.repository.UserSummaryOutbox;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * REV-15/REV-07 moderatör işlemleri (yalnız MODERATOR/ADMIN kullanıcı JWT'si; rol kontrolü güvenlik zincirindedir).
 * Kural: görünür olmayan (HIDDEN/ARCHIVED) inceleme genel listelerde, öne çıkanlarda, beğeni/rapor yollarında ve istatistik
 * activeCount'ta yoktur (VISIBLE beyaz liste); bağımsız puan ve tavsiye etkilenmez; beğeniler silinmez. Geçişler: HIDE yalnız
 * VISIBLE'dan; ARCHIVE VISIBLE/HIDDEN'dan ve geri alınamaz; RESTORE yalnız HIDDEN'dan. Her karar gerekçe ve incelemenin görülen
 * sürümüyle gelir (yazar bu arada düzenlediyse 409) ve denetim kaydına yazılır.
 */
@Service
public class ReviewModerationService {
    private static final String VISIBLE = "VISIBLE";
    private static final String HIDDEN = "HIDDEN";
    private static final String ARCHIVED = "ARCHIVED";
    private static final String DELETED = "DELETED";
    private final ReviewRepository reviews;
    private final ModerationRepository moderation;
    private final AccountWriteLock accounts;
    private final UserSummaryOutbox outbox;
    private final Clock clock;

    public ReviewModerationService(ReviewRepository reviews, ModerationRepository moderation, AccountWriteLock accounts,
            UserSummaryOutbox outbox, Clock clock) {
        this.reviews = reviews;
        this.moderation = moderation;
        this.accounts = accounts;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminReportedReviewPageResponse reportedReviews(ReportStatus status, int page, int size) {
        long total = moderation.countReportedReviews(status);
        var items = moderation.reportedReviews(status, size, (long) page * size);
        return new AdminReportedReviewPageResponse(items, page, size, total, total / size + (total % size == 0 ? 0 : 1));
    }

    @Transactional(readOnly = true)
    public AdminReviewResponse review(UUID reviewId) {
        return view(reviews.findById(reviewId).orElseThrow(ReviewModerationService::notFound));
    }

    @Transactional(readOnly = true)
    public List<AdminReportResponse> reports(UUID reviewId) {
        if (!reviews.existsById(reviewId)) throw notFound();
        return moderation.reportsOf(reviewId);
    }

    @Transactional(timeout = 4)
    public AdminReviewResponse moderate(UUID actor, UUID reviewId, ModerationAction action, String reason, long version) {
        String note = checkedReason(reason);
        accounts.requireWritable(actor);
        Review review = reviews.lockById(reviewId).orElseThrow(ReviewModerationService::notFound);
        if (DELETED.equals(review.getStatus())) throw notFound();
        if (review.getVersion() != version) throw new ReviewException(409, "REVIEW_VERSION_CONFLICT", "İnceleme siz görüntülerken değişti.");
        String next = switch (action) {
            case HIDE -> VISIBLE.equals(review.getStatus()) ? HIDDEN : null;
            case ARCHIVE -> VISIBLE.equals(review.getStatus()) || HIDDEN.equals(review.getStatus()) ? ARCHIVED : null;
            case RESTORE -> HIDDEN.equals(review.getStatus()) ? VISIBLE : null;
        };
        if (next == null) throw new ReviewException(409, "INVALID_MODERATION_TRANSITION", "Bu inceleme için bu eylem uygulanamaz.");
        review.moderate(next);
        reviews.saveAndFlush(review);
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        moderation.addAction(reviewId, actor, action.name(), note, review.getVersion(), now);
        // Yazarın görünür inceleme sayısı değişti (User profil sayacı).
        outbox.touch(review.getUserId(), now);
        return view(review);
    }

    /** İncelemenin tüm açık raporlarını kapatır. Açık rapor yoksa 409; kapanan rapor yeniden açılmaz. */
    @Transactional(timeout = 4)
    public int closeReports(UUID actor, UUID reviewId, ReportOutcome outcome, String reason) {
        String note = checkedReason(reason);
        accounts.requireWritable(actor);
        Review review = reviews.lockById(reviewId).orElseThrow(ReviewModerationService::notFound);
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        int closed = moderation.closeOpen(reviewId, outcome == ReportOutcome.REVIEWED ? ReportStatus.REVIEWED : ReportStatus.DISMISSED, actor, now);
        if (closed == 0) throw new ReviewException(409, "REPORT_NOT_OPEN", "Bu incelemenin açık raporu yok.");
        moderation.addAction(reviewId, actor, outcome == ReportOutcome.REVIEWED ? "REPORTS_REVIEWED" : "REPORTS_DISMISSED", note, review.getVersion(), now);
        return closed;
    }

    private AdminReviewResponse view(Review review) {
        boolean deleted = DELETED.equals(review.getStatus());
        return new AdminReviewResponse(review.getId(), review.getGameId(), deleted ? null : review.getBody(), review.getStatus(),
                review.getVersion(), review.getCreatedAt(), review.getUpdatedAt(), moderation.history(review.getId()));
    }

    private static String checkedReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500
                || reason.codePoints().anyMatch(value -> Character.isISOControl(value) && value != '\n' && value != '\r' && value != '\t'))
            throw new ReviewException(400, "Gerekçe 1–500 karakter düz metin olmalıdır.");
        return reason.strip();
    }

    private static ReviewException notFound() { return new ReviewException(404, "İnceleme bulunamadı."); }
}
