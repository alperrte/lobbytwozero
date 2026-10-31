package com.ltz.review.api.controller;

import com.ltz.review.api.dto.request.RatingSummariesRequest;
import com.ltz.review.api.dto.response.GameRatingSummaryResponse;
import com.ltz.review.api.dto.response.RatingSummariesResponse;
import com.ltz.review.application.service.RatingSummaryService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/review/games")
public class RatingSummaryController {
    private final RatingSummaryService service;

    public RatingSummaryController(RatingSummaryService service) { this.service = service; }

    @GetMapping("/{gameId}/rating-summary")
    public GameRatingSummaryResponse get(@PathVariable UUID gameId) { return service.get(gameId); }

    @PostMapping("/rating-summaries")
    public RatingSummariesResponse getAll(@Valid @RequestBody RatingSummariesRequest request) {
        return service.getAll(request.gameIds());
    }
}
