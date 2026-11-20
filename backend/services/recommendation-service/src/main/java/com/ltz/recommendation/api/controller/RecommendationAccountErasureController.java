package com.ltz.recommendation.api.controller;

import com.ltz.recommendation.application.service.PersonalizedRecommendationService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RecommendationAccountErasureController {
    private final PersonalizedRecommendationService service;
    public RecommendationAccountErasureController(PersonalizedRecommendationService service) { this.service = service; }

    @DeleteMapping("/internal/v1/accounts/{accountId}")
    public ResponseEntity<Void> erase(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID accountId) {
        if (!"svc:auth-service".equals(jwt.getSubject()) || !accountId.toString().equals(jwt.getClaims().get("account_id"))) {
            throw new AccessDeniedException("Account binding required");
        }
        service.erase(accountId);
        return ResponseEntity.noContent().header("Cache-Control", "private, no-store").build();
    }
}
