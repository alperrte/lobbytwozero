package com.ltz.game.api.controller;

import com.ltz.game.api.dto.request.ReassignGameReferenceRequest;
import com.ltz.game.api.dto.request.SplitGameReferenceRequest;
import com.ltz.game.application.port.Actor;
import com.ltz.game.application.service.AdminGameIdentityService;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/v1/game/admin/identity") @RequiredArgsConstructor
public class AdminGameIdentityController {
    private final AdminGameIdentityService identity;

    @PostMapping("/reassign")
    public Map<String, UUID> reassign(Actor actor, @Valid @RequestBody ReassignGameReferenceRequest request) {
        return Map.of("gameId", identity.reassign(actor, request.provider(), request.externalId(),
                request.expectedGameId(), request.targetGameId(), request.evidence()));
    }

    @PostMapping("/split")
    public Map<String, UUID> split(Actor actor, @Valid @RequestBody SplitGameReferenceRequest request) {
        return Map.of("gameId", identity.split(actor, request.provider(), request.externalId(),
                request.expectedGameId(), request.evidence()));
    }
}
