package com.ltz.review.api.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record GameRatingSummaryResponse(UUID gameId, BigDecimal averageRating, long ratingCount) {}
