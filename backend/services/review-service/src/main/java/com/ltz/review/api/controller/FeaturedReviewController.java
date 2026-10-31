package com.ltz.review.api.controller;

import com.ltz.review.api.dto.response.FeaturedReviewsResponse;
import com.ltz.review.application.service.FeaturedReviewService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/review/games/{gameId}/reviews/featured")
public class FeaturedReviewController {
    private final FeaturedReviewService service;

    public FeaturedReviewController(FeaturedReviewService service) { this.service = service; }

    @GetMapping
    public FeaturedReviewsResponse get(@PathVariable UUID gameId, @AuthenticationPrincipal Jwt actor,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @RequestParam(defaultValue = "0") @Min(0) int minLikes) {
        return service.get(gameId, UUID.fromString(actor.getSubject()), page, size, minLikes);
    }
}
