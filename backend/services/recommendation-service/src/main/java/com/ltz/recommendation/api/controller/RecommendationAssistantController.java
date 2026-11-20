package com.ltz.recommendation.api.controller;

import com.ltz.recommendation.api.dto.request.AssistantRequest;
import com.ltz.recommendation.api.dto.response.AssistantResponse;
import com.ltz.recommendation.application.port.PersonalizationSource.Actor;
import com.ltz.recommendation.application.service.RecommendationAssistantService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RecommendationAssistantController {
    private final RecommendationAssistantService service;
    public RecommendationAssistantController(RecommendationAssistantService service) { this.service = service; }

    @PostMapping("/api/v1/recommendation/assistant")
    public ResponseEntity<AssistantResponse> advise(@AuthenticationPrincipal Jwt jwt, @RequestBody @Valid AssistantRequest request) {
        var actor = new Actor(UUID.fromString(jwt.getSubject()), "Bearer " + jwt.getTokenValue());
        return ResponseEntity.ok().header("Cache-Control", "private, no-store")
                .body(AssistantResponse.from(service.advise(actor, request.toQuery())));
    }
}
