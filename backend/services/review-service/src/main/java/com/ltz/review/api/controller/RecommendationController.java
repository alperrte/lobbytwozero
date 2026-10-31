package com.ltz.review.api.controller;

import com.ltz.review.api.dto.request.RecommendGameRequest;
import com.ltz.review.api.dto.response.RecommendationResponse;
import com.ltz.review.application.service.RecommendationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/review/games/{gameId}/recommendation")
public class RecommendationController {
    private final RecommendationService service;

    public RecommendationController(RecommendationService service) { this.service = service; }

    @PutMapping
    public RecommendationResponse put(@PathVariable UUID gameId, @AuthenticationPrincipal Jwt actor, @Valid @RequestBody RecommendGameRequest request) {
        return service.recommend(UUID.fromString(actor.getSubject()), gameId, request.recommended());
    }

    @GetMapping
    public RecommendationResponse get(@PathVariable UUID gameId, @AuthenticationPrincipal Jwt actor) {
        return service.get(UUID.fromString(actor.getSubject()), gameId);
    }
}
