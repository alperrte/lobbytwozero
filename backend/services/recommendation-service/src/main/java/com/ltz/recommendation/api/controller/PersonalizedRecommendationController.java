package com.ltz.recommendation.api.controller;

import com.ltz.recommendation.api.dto.request.GenerateRecommendationsRequest;
import com.ltz.recommendation.api.dto.request.RecommendationFeedbackRequest;
import com.ltz.recommendation.api.dto.response.RecommendationRunResponse;
import com.ltz.recommendation.application.port.PersonalizationSource.Actor;
import com.ltz.recommendation.application.port.PersonalizationSource.Filters;
import com.ltz.recommendation.application.port.RecommendationHistory.Feedback;
import com.ltz.recommendation.application.port.RecommendationHistory.Page;
import com.ltz.recommendation.application.service.PersonalizedRecommendationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recommendation")
public class PersonalizedRecommendationController {
    private final PersonalizedRecommendationService service;
    public PersonalizedRecommendationController(PersonalizedRecommendationService service) { this.service = service; }

    @PostMapping("/games")
    public ResponseEntity<RecommendationRunResponse> generate(@AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid GenerateRecommendationsRequest request) {
        var run = service.generate(actor(jwt), new Filters(request.genre(), request.platform(), request.size() == null ? 20 : request.size()));
        return response(RecommendationRunResponse.from(run));
    }

    @GetMapping("/runs")
    public ResponseEntity<Page> history(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) UUID beforeRunId,
            @RequestParam(defaultValue = "20") int size) {
        return response(service.history(actor(jwt), beforeRunId, size));
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<RecommendationRunResponse> run(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID runId) {
        return response(RecommendationRunResponse.from(service.read(actor(jwt), runId)));
    }

    @PutMapping("/runs/{runId}/items/{itemId}/feedback")
    public ResponseEntity<Feedback> feedback(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID runId,
            @PathVariable UUID itemId, @RequestBody @Valid RecommendationFeedbackRequest request) {
        return response(service.feedback(actor(jwt), runId, itemId, request.feedback(), request.version()));
    }

    @GetMapping("/runs/{runId}/items/{itemId}/feedback")
    public ResponseEntity<Feedback> feedbackState(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID runId, @PathVariable UUID itemId) {
        return response(service.feedbackState(actor(jwt), runId, itemId));
    }

    private static Actor actor(Jwt jwt) { return new Actor(UUID.fromString(jwt.getSubject()), "Bearer " + jwt.getTokenValue()); }
    private static <T> ResponseEntity<T> response(T body) {
        return ResponseEntity.ok().header("Cache-Control", "private, no-store").body(body);
    }
}
