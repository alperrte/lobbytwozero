package com.ltz.review.api.controller;

import com.ltz.review.api.dto.request.RateGameRequest;
import com.ltz.review.api.dto.response.RatingResponse;
import com.ltz.review.application.service.RatingService;
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
@RequestMapping("/api/v1/review/games/{gameId}/rating")
public class RatingController {
    private final RatingService service;

    public RatingController(RatingService service) { this.service = service; }

    @PutMapping
    public RatingResponse rate(@PathVariable UUID gameId, @AuthenticationPrincipal Jwt actor, @Valid @RequestBody RateGameRequest request) {
        return service.rate(UUID.fromString(actor.getSubject()), gameId, request.rating());
    }

    @GetMapping
    public RatingResponse get(@PathVariable UUID gameId, @AuthenticationPrincipal Jwt actor) {
        return service.get(UUID.fromString(actor.getSubject()), gameId);
    }
}
