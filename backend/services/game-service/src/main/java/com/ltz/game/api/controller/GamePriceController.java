package com.ltz.game.api.controller;

import com.ltz.game.application.port.GamePriceProvider.Market;
import com.ltz.game.application.service.GamePriceService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/game/games/prices")
public class GamePriceController {
    private final GamePriceService prices;
    @GetMapping public org.springframework.http.ResponseEntity<com.ltz.game.api.dto.response.GamePricesResponse> read(
            @RequestParam List<UUID> gameIds,@RequestParam String country,@RequestParam String currency) {
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(prices.read(gameIds,new Market(country,currency)));
    }
}
