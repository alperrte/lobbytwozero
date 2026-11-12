package com.ltz.review.api.dto.response;

/**
 * Öne çıkan liste öğesi. {@code likedByActor} yalnız istek sahibinin oyudur (oy veren kimlikleri dönmez); sayı ve aktör durumu
 * ayrı alanlardır. Kendi incelemesinde aktör oy veremediğinden değer false'tur.
 */
public record FeaturedReviewResponse(ReviewResponse review, long likeCount, boolean likedByActor) {}
