package com.ltz.game.api.controller;

import com.ltz.game.api.dto.request.GameDiscoveryRequest;
import com.ltz.game.api.dto.response.GameDiscoveryResponse;
import com.ltz.game.application.service.DiscoveryQuery;
import com.ltz.game.application.service.GameDiscoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/v1/game/games") @RequiredArgsConstructor
public class GameDiscoveryController {
    private final GameDiscoveryService service;
    @GetMapping
    public GameDiscoveryResponse list(@ModelAttribute GameDiscoveryRequest request) { return service.list(request.toQuery()); }
    @GetMapping("/search")
    public GameDiscoveryResponse search(@RequestParam String q,@ModelAttribute GameDiscoveryRequest request) {
        request.setQ(q);
        return service.list(request.toQuery());
    }
    @GetMapping("/popular")
    public GameDiscoveryResponse popular(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.list(DiscoveryQuery.popular(page,size));
    }
}
