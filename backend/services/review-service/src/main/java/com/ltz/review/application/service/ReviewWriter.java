package com.ltz.review.application.service;

import com.ltz.review.api.dto.response.ReviewResponse;
import com.ltz.review.application.mapper.ReviewMapper;
import com.ltz.review.domain.entity.Review;
import com.ltz.review.domain.exception.ReviewException;
import com.ltz.review.infrastructure.persistence.repository.ReviewRepository;
import com.ltz.review.infrastructure.persistence.repository.AccountWriteLock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewWriter {
    private final ReviewRepository reviews;
    private final AccountWriteLock accounts;

    public ReviewWriter(ReviewRepository reviews, AccountWriteLock accounts) {
        this.reviews = reviews;
        this.accounts = accounts;
    }

    @Transactional(timeout = 4)
    public ReviewResponse create(UUID userId, UUID gameId, String body) {
        accounts.requireWritable(userId);
        if (reviews.existsByUserIdAndGameIdAndStatusNot(userId, gameId, "DELETED"))
            throw new ReviewException(409, "Bu oyun için zaten aktif incelemeniz var.");
        var review = reviews.saveAndFlush(new Review(userId, gameId, body, now()));
        return ReviewMapper.response(review, userId);
    }

    @Transactional(timeout = 4)
    public ReviewResponse update(UUID userId, UUID reviewId, long version, String body) {
        accounts.requireWritable(userId);
        var review = owned(userId, reviewId);
        if (!"VISIBLE".equals(review.getStatus())) throw new ReviewException(404, "İnceleme bulunamadı.");
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
        if ("DELETED".equals(review.getStatus())) return;
        if (review.getVersion() != version) throw new ReviewException(409, "İnceleme sürümü güncel değil.");
        review.remove(now());
        reviews.flush();
    }

    private static Instant now() { return Instant.now().truncatedTo(ChronoUnit.MICROS); }
}
