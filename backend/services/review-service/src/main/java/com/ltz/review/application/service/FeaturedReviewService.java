package com.ltz.review.application.service;

import com.ltz.review.api.dto.response.FeaturedReviewsResponse;
import com.ltz.review.infrastructure.persistence.repository.FeaturedReviewRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeaturedReviewService {
    private final FeaturedReviewRepository reviews;

    public FeaturedReviewService(FeaturedReviewRepository reviews) { this.reviews = reviews; }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 4)
    public FeaturedReviewsResponse get(UUID game, UUID actor, int page, int size, int minLikes) {
        long total = reviews.count(game, minLikes);
        var items = reviews.find(game, actor, minLikes, size, (long) page * size);
        long pages = total / size + (total % size == 0 ? 0 : 1);
        return new FeaturedReviewsResponse(items, page, size, total, pages);
    }
}
