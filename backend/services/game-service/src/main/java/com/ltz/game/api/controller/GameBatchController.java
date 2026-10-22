package com.ltz.game.api.controller;

import com.ltz.game.api.dto.request.GameBatchRequest;
import com.ltz.game.api.dto.response.GameBatchResponse;
import com.ltz.game.application.service.GameBatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Herkese açık toplu kart okuması (GAME-15). Kimlik gerektirmez; yalnız herkese açık katalog verisi döner. */
@RestController @RequestMapping("/api/v1/game/games") @RequiredArgsConstructor
public class GameBatchController {
    private final GameBatchService service;
    @PostMapping("/batch")
    public GameBatchResponse batch(@Valid @RequestBody GameBatchRequest request) { return service.cards(request.gameIds()); }
}
