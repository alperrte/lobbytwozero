package com.ltz.review.application.service;

import com.ltz.review.api.dto.response.ReviewResponse;
import com.ltz.review.application.mapper.ReviewMapper;
import com.ltz.review.domain.entity.Review;
import com.ltz.review.domain.exception.ReviewException;
import com.ltz.review.infrastructure.persistence.repository.ReviewRepository;
import com.ltz.review.infrastructure.persistence.repository.AccountWriteLock;
import com.ltz.review.infrastructure.persistence.repository.ReviewLikeRepository;
import com.ltz.review.infrastructure.persistence.repository.UserSummaryOutbox;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewWriter {
    private static final String DELETED = "DELETED";

    private final ReviewRepository reviews;
    private final AccountWriteLock accounts;
    private final ReviewLikeRepository likes;
    private final UserSummaryOutbox outbox;

    public ReviewWriter(ReviewRepository reviews, AccountWriteLock accounts, ReviewLikeRepository likes, UserSummaryOutbox outbox) {
        this.reviews = reviews;
        this.accounts = accounts;
        this.likes = likes;
        this.outbox = outbox;
    }

    @Transactional(timeout = 4)
    public ReviewResponse create(UUID userId, UUID gameId, String body) {
        accounts.requireWritable(userId);
        if (reviews.existsByUserIdAndGameIdAndStatusNot(userId, gameId, DELETED))
            throw new ReviewException(409, "Bu oyun için zaten aktif incelemeniz var.");
        var review = reviews.saveAndFlush(new Review(userId, gameId, body, now()));
        outbox.touch(userId, now());
        return ReviewMapper.response(review, userId);
    }

    @Transactional(timeout = 4)
    public ReviewResponse update(UUID userId, UUID reviewId, long version, String body) {
        accounts.requireWritable(userId);
        var review = owned(userId, reviewId);
        if (DELETED.equals(review.getStatus())) throw new ReviewException(404, "İnceleme bulunamadı.");
        if (!"VISIBLE".equals(review.getStatus())) throw moderated();
        if (review.getVersion() != version) throw new ReviewException(409, "İnceleme sürümü güncel değil.");
        review.edit(body, now());
        reviews.flush();
        return ReviewMapper.response(review, userId);
    }

    private Review owned(UUID userId, UUID reviewId) {
        var review = reviews.lockById(reviewId).orElseThrow(() -> new ReviewException(404, "İnceleme bulunamadı."));
        if (!userId.equals(review.getUserId())) throw new ReviewException(403, "Bu inceleme için yetkiniz yok.");
        return review;
    }

    @Transactional(timeout = 4)
    public void delete(UUID userId, UUID reviewId, long version) {
        accounts.requireWritable(userId);
        if (version < 0) throw new ReviewException(400, "Sürüm negatif olamaz.");
        var review = owned(userId, reviewId);
        if (DELETED.equals(review.getStatus())) return;
        // ARCHIVED kalıcı moderasyondur: yazar silerek kanıtı yok edemez. HIDDEN geri alınabilir ve yazar tarafından silinebilir.
        if ("ARCHIVED".equals(review.getStatus())) throw moderated();
        if (review.getVersion() != version) throw new ReviewException(409, "İnceleme sürümü güncel değil.");
        review.remove(now());
        reviews.flush();
        likes.removeForReview(reviewId);
        outbox.touch(userId, now());
    }

    private static ReviewException moderated() {
        return new ReviewException(403, "REVIEW_MODERATED", "Bu inceleme moderasyon nedeniyle değiştirilemez.");
    }

    private static Instant now() { return Instant.now().truncatedTo(ChronoUnit.MICROS); }
}
