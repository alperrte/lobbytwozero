package com.ltz.review.application.service;

import com.ltz.review.api.dto.response.ReviewLikeResponse;
import com.ltz.review.domain.entity.Review;
import com.ltz.review.domain.exception.ReviewException;
import com.ltz.review.infrastructure.persistence.repository.AccountWriteLock;
import com.ltz.review.infrastructure.persistence.repository.ReviewLikeRepository;
import com.ltz.review.infrastructure.persistence.repository.ReviewRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewLikeService {
    private final ReviewRepository reviews;
    private final ReviewLikeRepository likes;
    private final AccountWriteLock accounts;

    public ReviewLikeService(ReviewRepository reviews, ReviewLikeRepository likes, AccountWriteLock accounts) {
        this.reviews = reviews;
        this.likes = likes;
        this.accounts = accounts;
    }

    @Transactional(timeout = 4)
    public ReviewLikeResponse like(UUID actor, UUID reviewId) {
        accounts.requireWritable(actor);
        var review = visible(reviews.lockById(reviewId).orElseThrow(ReviewLikeService::missing));
        if (actor.equals(review.getUserId())) throw new ReviewException(403, "Kendi incelemenizi beğenemezsiniz.");
        likes.add(reviewId, actor);
        return likes.state(reviewId, actor);
    }

    @Transactional(timeout = 4)
    public ReviewLikeResponse unlike(UUID actor, UUID reviewId) {
        accounts.requireWritable(actor);
        visible(reviews.lockById(reviewId).orElseThrow(ReviewLikeService::missing));
        likes.remove(reviewId, actor);
        return likes.state(reviewId, actor);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 4)
    public ReviewLikeResponse get(UUID actor, UUID reviewId) {
        visible(reviews.findById(reviewId).orElseThrow(ReviewLikeService::missing));
        return likes.state(reviewId, actor);
    }

    private static Review visible(Review review) {
        if (!"VISIBLE".equals(review.getStatus())) throw missing();
        return review;
    }

    private static ReviewException missing() { return new ReviewException(404, "İnceleme bulunamadı."); }
}
