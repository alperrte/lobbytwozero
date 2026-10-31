package com.ltz.review.api.controller;

import com.ltz.review.api.dto.response.ReviewLikeResponse;
import com.ltz.review.application.service.ReviewLikeService;
import com.ltz.review.domain.exception.ReviewException;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/review/reviews/{reviewId}/like")
public class ReviewLikeController {
    private final ReviewLikeService service;

    public ReviewLikeController(ReviewLikeService service) { this.service = service; }

    @PutMapping
    public ReviewLikeResponse like(@PathVariable UUID reviewId, @AuthenticationPrincipal Jwt actor,
            @RequestBody(required = false) String body) {
        requireEmptyBody(body);
        return service.like(UUID.fromString(actor.getSubject()), reviewId);
    }

    @DeleteMapping
    public ReviewLikeResponse unlike(@PathVariable UUID reviewId, @AuthenticationPrincipal Jwt actor,
            @RequestBody(required = false) String body) {
        requireEmptyBody(body);
        return service.unlike(UUID.fromString(actor.getSubject()), reviewId);
    }

    @GetMapping
    public ReviewLikeResponse get(@PathVariable UUID reviewId, @AuthenticationPrincipal Jwt actor) {
        return service.get(UUID.fromString(actor.getSubject()), reviewId);
    }

    private static void requireEmptyBody(String body) {
        if (body != null && !body.isBlank()) throw new ReviewException(400, "Bu işlem istek gövdesi kabul etmez.");
    }
}
