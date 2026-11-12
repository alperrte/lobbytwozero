package com.ltz.review.application.service;

import com.ltz.review.api.dto.response.ReviewReportResponse;
import com.ltz.review.domain.enums.ReportReason;
import com.ltz.review.domain.exception.ReviewException;
import com.ltz.review.infrastructure.persistence.repository.AccountWriteLock;
import com.ltz.review.infrastructure.persistence.repository.ReviewReportRepository;
import com.ltz.review.infrastructure.persistence.repository.ReviewRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * REV-07 (Review tarafı): uygunsuz inceleme raporu. Rapor yalnız yerel olarak kalıcılaşır; Admin Service'e teslim henüz
 * yoktur ve "iletildi/karara bağlandı" iddiası kurulmaz. Politikalar: kendi incelemesi raporlanamaz (403), yalnız görünür
 * incelemeye rapor (404), aynı raportör aynı incelemeyi bir kez raporlar (409), raportör kimliği hiçbir yanıtta dönmez.
 */
@Service
public class ReviewReportService {
    static final String ALREADY_REPORTED = "REPORT_ALREADY_EXISTS";
    static final String OWN_REVIEW = "REPORT_OWN_REVIEW";
    private final ReviewRepository reviews;
    private final ReviewReportRepository reports;
    private final AccountWriteLock accounts;
    private final Clock clock;

    public ReviewReportService(ReviewRepository reviews, ReviewReportRepository reports, AccountWriteLock accounts, Clock clock) {
        this.reviews = reviews;
        this.reports = reports;
        this.accounts = accounts;
        this.clock = clock;
    }

    @Transactional(timeout = 4)
    public ReviewReportResponse create(UUID actor, UUID reviewId, ReportReason reason, String detail) {
        String checkedDetail = checkedDetail(detail);
        accounts.requireWritable(actor);
        var review = reviews.lockById(reviewId).filter(value -> "VISIBLE".equals(value.getStatus()))
                .orElseThrow(() -> new ReviewException(404, "İnceleme bulunamadı."));
        if (actor.equals(review.getUserId()))
            throw new ReviewException(403, OWN_REVIEW, "Kendi incelemenizi raporlayamazsınız.");
        UUID id = UUID.randomUUID();
        if (reports.insertIfAbsent(id, reviewId, actor, reason, checkedDetail, clock.instant()) == 0)
            throw new ReviewException(409, ALREADY_REPORTED, "Bu incelemeyi zaten raporladınız.");
        return reports.find(reviewId, actor).orElseThrow();
    }

    @Transactional(readOnly = true)
    public ReviewReportResponse mine(UUID actor, UUID reviewId) {
        return reports.find(reviewId, actor).orElseThrow(() -> new ReviewException(404, "Rapor bulunamadı."));
    }

    /** Açıklama isteğe bağlı: boşsa null; en çok 500 karakter, satır sonu/sekme dışında denetim karakteri yok. */
    static String checkedDetail(String detail) {
        if (detail == null) return null;
        if (detail.length() > 500 || detail.codePoints().anyMatch(value -> Character.isISOControl(value) && value != '\n' && value != '\r' && value != '\t'))
            throw new ReviewException(400, "Rapor açıklaması en çok 500 karakter düz metin olmalıdır.");
        String stripped = detail.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
