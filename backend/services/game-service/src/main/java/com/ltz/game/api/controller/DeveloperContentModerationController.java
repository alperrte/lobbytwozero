package com.ltz.game.api.controller;

import com.ltz.game.api.dto.request.ModerationRequest;
import com.ltz.game.api.dto.response.ModerationResponse;
import com.ltz.game.application.port.Actor;
import com.ltz.game.application.service.DeveloperContentModerationService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/v1/game/moderation") @RequiredArgsConstructor
public class DeveloperContentModerationController {
    private final DeveloperContentModerationService service;
    @PostMapping("/games/{gameId}/developer-content/actions")
    public ModerationResponse apply(Actor actor, @PathVariable UUID gameId, @Valid @RequestBody ModerationRequest request) {
        return service.apply(actor, gameId, request.target(), request.targetId(), request.action(), request.reason());
    }
}
