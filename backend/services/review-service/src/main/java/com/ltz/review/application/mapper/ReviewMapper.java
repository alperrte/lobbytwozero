package com.ltz.review.application.mapper;

import com.ltz.review.api.dto.response.ReviewResponse;
import com.ltz.review.domain.entity.Review;
import java.util.UUID;

public final class ReviewMapper {
    private ReviewMapper() {}

    public static ReviewResponse response(Review review, UUID actor) {
        return new ReviewResponse(review.getId(), review.getGameId(), review.getBody(), review.getUserId(), actor.equals(review.getUserId()), review.getStatus(),
                review.getVersion(), review.getCreatedAt(), review.getUpdatedAt());
    }
}
