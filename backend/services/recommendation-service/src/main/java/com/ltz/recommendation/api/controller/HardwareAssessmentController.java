package com.ltz.recommendation.api.controller;

import com.ltz.recommendation.api.dto.request.HardwareAssessmentRequest;
import com.ltz.recommendation.api.dto.response.HardwareAssessmentResponse;
import com.ltz.recommendation.application.port.PersonalizationSource.Actor;
import com.ltz.recommendation.application.service.HardwareAssessmentService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HardwareAssessmentController {
    private final HardwareAssessmentService service;
    public HardwareAssessmentController(HardwareAssessmentService service) { this.service = service; }

    @PostMapping("/api/v1/recommendation/hardware/assessments")
    public ResponseEntity<HardwareAssessmentResponse> assess(@AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid HardwareAssessmentRequest request) {
        var actor = new Actor(UUID.fromString(jwt.getSubject()), "Bearer " + jwt.getTokenValue());
        var result = service.assess(actor, request.hardwareProfileId(), request.gameId(), request.platform(),
                request.resolution(), request.graphicsPreset());
        return ResponseEntity.ok().header("Cache-Control", "private, no-store").body(HardwareAssessmentResponse.from(result));
    }
}
