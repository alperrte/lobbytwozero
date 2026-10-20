package com.ltz.game.api.controller;

import com.ltz.game.api.dto.response.*;
import com.ltz.game.application.service.GameViewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/game") @RequiredArgsConstructor
public class GameViewController {
    private final GameViewService service;
    @GetMapping("/{gameId}/card")
    public GameCardResponse card(@PathVariable UUID gameId) { return service.card(gameId); }
    @GetMapping("/games/{gameId}")
    public GameDetailResponse detail(@PathVariable UUID gameId) { return service.detail(gameId); }
}
