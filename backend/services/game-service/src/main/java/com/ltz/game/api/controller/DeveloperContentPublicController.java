package com.ltz.game.api.controller;

import com.ltz.game.api.dto.response.DeveloperContentResponse;
import com.ltz.game.application.service.DeveloperContentService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/v1/game/games") @RequiredArgsConstructor
public class DeveloperContentPublicController {
    private final DeveloperContentService service;
    @GetMapping("/{gameId}/developer-content")
    public DeveloperContentResponse get(@PathVariable UUID gameId) { return service.publicView(gameId); }
}
