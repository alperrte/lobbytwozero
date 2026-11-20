package com.ltz.performance.api.controller;

import com.ltz.performance.api.dto.response.PerformanceEvidenceResponse;
import com.ltz.performance.application.service.PerformanceEvidenceService;
import com.ltz.performance.domain.entity.MeasurementContext;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PerformanceEvidenceController {
    private final PerformanceEvidenceService service;
    public PerformanceEvidenceController(PerformanceEvidenceService service) { this.service = service; }

    @GetMapping("/internal/v1/accounts/{accountId}/games/{gameId}/performance-evidence")
    public ResponseEntity<PerformanceEvidenceResponse> evidence(@PathVariable UUID accountId, @PathVariable UUID gameId,
            @RequestParam UUID hardwareProfileId, @RequestParam long hardwareProfileVersion, @RequestParam String resolution,
            @RequestParam String graphicsPreset, @RequestParam String renderingMode, @AuthenticationPrincipal Jwt jwt) {
        if (!"svc:recommendation-service".equals(jwt.getSubject()) || !accountId.toString().equals(jwt.getClaims().get("account_id"))) {
            throw new AccessDeniedException("Account binding required");
        }
        var context = new MeasurementContext(hardwareProfileVersion, resolution, graphicsPreset, renderingMode);
        return ResponseEntity.ok().header("Cache-Control", "private, no-store")
                .body(service.evidence(accountId, gameId, hardwareProfileId, context));
    }
}
