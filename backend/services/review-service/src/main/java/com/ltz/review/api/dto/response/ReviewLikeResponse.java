package com.ltz.review.api.dto.response;

import java.util.UUID;

public record ReviewLikeResponse(UUID reviewId, long likeCount, boolean likedByActor) {}
