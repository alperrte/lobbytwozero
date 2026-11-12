package com.ltz.review.api.controller;

import com.ltz.review.api.dto.request.ReportReviewRequest;
import com.ltz.review.api.dto.response.ReviewReportResponse;
import com.ltz.review.application.service.ReviewReportService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/review/reviews/{reviewId}/reports")
public class ReviewReportController {
    private final ReviewReportService service;

    public ReviewReportController(ReviewReportService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<ReviewReportResponse> create(@PathVariable UUID reviewId, @AuthenticationPrincipal Jwt actor,
            @Valid @RequestBody ReportReviewRequest request) {
        var response = service.create(UUID.fromString(actor.getSubject()), reviewId, request.reason(), request.detail());
        return ResponseEntity.created(URI.create("/api/v1/review/reviews/" + reviewId + "/reports/mine")).body(response);
    }

    /** Raportörün bu incelemeye yaptığı kendi raporu; yoksa 404. Başkasının raporu dönmez. */
    @GetMapping("/mine")
    public ReviewReportResponse mine(@PathVariable UUID reviewId, @AuthenticationPrincipal Jwt actor) {
        return service.mine(UUID.fromString(actor.getSubject()), reviewId);
    }
}
