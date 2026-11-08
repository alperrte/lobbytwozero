package com.ltz.game.api.controller;

import com.ltz.game.api.dto.response.GameCardResponse;
import com.ltz.game.api.dto.response.GameDetailResponse;
import com.ltz.game.api.dto.response.GameDlcPageResponse;
import com.ltz.game.application.service.GameViewService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/v1/game") @RequiredArgsConstructor
public class GameViewController {
    private final GameViewService service;
    @GetMapping("/{gameId}/card")
    public GameCardResponse card(@PathVariable UUID gameId) { return service.card(gameId); }
    @GetMapping("/games/{gameId}")
    public GameDetailResponse detail(@PathVariable UUID gameId, @RequestParam(required = false) String lang) { return service.detail(gameId, lang); }
    /** Ana oyunun DLC'leri (sayfalı, herkese açık salt okunur). */
    @GetMapping("/games/{gameId}/dlc")
    public GameDlcPageResponse dlc(@PathVariable UUID gameId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) { return service.dlc(gameId, page, size); }
}
