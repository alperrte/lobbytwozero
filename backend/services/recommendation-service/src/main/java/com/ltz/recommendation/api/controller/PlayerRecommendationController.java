package com.ltz.recommendation.api.controller;

import com.ltz.recommendation.application.port.PersonalizationSource.Actor;
import com.ltz.recommendation.application.service.PlayerRecommendationService;
import com.ltz.recommendation.application.service.PlayerRecommendationService.Kind;
import com.ltz.recommendation.application.service.PlayerRecommendationService.Result;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REC-13/14. İzleyici kimliği yalnız token'dan gelir; istemci aday kimliği veya "dahil et" bayrağı veremez. */
@RestController
@RequestMapping("/api/v1/recommendation")
public class PlayerRecommendationController {
    private final PlayerRecommendationService service;
    public PlayerRecommendationController(PlayerRecommendationService service) { this.service = service; }

    @GetMapping("/players")
    public ResponseEntity<Result> players(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String genre, @RequestParam(required = false) String platform) {
        return response(service.recommend(actor(jwt), Kind.SIMILAR_PLAYERS, size, genre, platform));
    }

    @GetMapping("/friends")
    public ResponseEntity<Result> friends(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String genre, @RequestParam(required = false) String platform) {
        return response(service.recommend(actor(jwt), Kind.FRIEND_SUGGESTIONS, size, genre, platform));
    }

    private static Actor actor(Jwt jwt) { return new Actor(UUID.fromString(jwt.getSubject()), "Bearer " + jwt.getTokenValue()); }
    private static ResponseEntity<Result> response(Result body) {
        return ResponseEntity.ok().header("Cache-Control", "private, no-store").body(body);
    }
}
