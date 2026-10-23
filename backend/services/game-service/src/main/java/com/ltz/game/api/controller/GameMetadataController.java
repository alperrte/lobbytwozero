package com.ltz.game.api.controller;
import com.ltz.game.api.dto.response.GameMetadataResponse;
import com.ltz.game.application.service.GameMetadataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/game") @RequiredArgsConstructor
public class GameMetadataController {
    private final GameMetadataService service;
    @GetMapping("/{gameId}/metadata")
    public GameMetadataResponse get(@PathVariable UUID gameId) { return service.get(gameId); }
}
