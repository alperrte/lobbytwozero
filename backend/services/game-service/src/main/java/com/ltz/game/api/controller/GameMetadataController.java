package com.ltz.game.api.controller;
import com.ltz.game.api.dto.response.GameMetadataResponse;
import com.ltz.game.application.service.GameMetadataService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController @RequestMapping("/api/v1/game") @RequiredArgsConstructor
public class GameMetadataController {
    private final GameMetadataService service;
    @GetMapping("/{gameId}/metadata")
    public GameMetadataResponse get(@PathVariable UUID gameId) { return service.get(gameId); }
}
