package com.ltz.review.api.controller;

import com.ltz.review.api.dto.response.GameReviewStatisticsResponse;
import com.ltz.review.application.service.ReviewStatisticsService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/review/games")
public class ReviewStatisticsController {
    private final ReviewStatisticsService service;

    public ReviewStatisticsController(ReviewStatisticsService service) { this.service = service; }

    @GetMapping("/{gameId}/statistics")
    public GameReviewStatisticsResponse get(@PathVariable UUID gameId) { return service.get(gameId); }
}
