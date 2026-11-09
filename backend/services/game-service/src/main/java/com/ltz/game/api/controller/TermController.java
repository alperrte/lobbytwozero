package com.ltz.game.api.controller;

import com.ltz.game.api.dto.response.TermListResponse;
import com.ltz.game.application.service.CatalogTermQueryService;
import com.ltz.game.domain.enums.TermKind;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Herkese açık, salt okunur terim listesi (tür ve platform filtre önerileri). */
@RestController @RequestMapping("/api/v1/game/terms") @RequiredArgsConstructor
public class TermController {
    private final CatalogTermQueryService terms;

    @GetMapping
    public ResponseEntity<TermListResponse> list(@RequestParam TermKind kind, @RequestParam(defaultValue = "200") int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic()).body(terms.list(kind, limit));
    }
}
