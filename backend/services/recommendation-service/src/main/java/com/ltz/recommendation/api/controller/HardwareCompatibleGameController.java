package com.ltz.recommendation.api.controller;

import com.ltz.recommendation.application.port.PersonalizationSource.Actor;
import com.ltz.recommendation.application.service.HardwareCompatibleGameService;
import com.ltz.recommendation.application.service.HardwareCompatibleGameService.Result;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REC-06/16. Profil sahipliği donanım servisinde token ile doğrulanır; istemci aday listesi veremez. */
@RestController
@RequestMapping("/api/v1/recommendation")
public class HardwareCompatibleGameController {
    private final HardwareCompatibleGameService service;
    public HardwareCompatibleGameController(HardwareCompatibleGameService service) { this.service = service; }

    @GetMapping("/games/hardware-compatible")
    public ResponseEntity<Result> compatible(@AuthenticationPrincipal Jwt jwt, @RequestParam UUID profileId,
            @RequestParam String platform, @RequestParam(required = false) String genre,
            @RequestParam(required = false) Integer size) {
        var actor = new Actor(UUID.fromString(jwt.getSubject()), "Bearer " + jwt.getTokenValue());
        return ResponseEntity.ok().header("Cache-Control", "private, no-store")
                .body(service.recommend(actor, profileId, platform, genre, size));
    }
}
