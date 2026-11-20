package com.ltz.recommendation.api.controller;

import com.ltz.recommendation.application.port.PersonalizationSource.Actor;
import com.ltz.recommendation.application.service.EventRecommendationService;
import com.ltz.recommendation.application.service.EventRecommendationService.Result;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REC-15. İzleyici kimliği yalnız token'dan gelir; istemci etkinlik veya kullanıcı kimliği veremez. */
@RestController
@RequestMapping("/api/v1/recommendation")
public class EventRecommendationController {
    private final EventRecommendationService service;
    public EventRecommendationController(EventRecommendationService service) { this.service = service; }

    @GetMapping("/events")
    public ResponseEntity<Result> events(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) Integer size) {
        Actor actor = new Actor(UUID.fromString(jwt.getSubject()), "Bearer " + jwt.getTokenValue());
        return ResponseEntity.ok().header("Cache-Control", "private, no-store").body(service.recommend(actor, size));
    }
}
