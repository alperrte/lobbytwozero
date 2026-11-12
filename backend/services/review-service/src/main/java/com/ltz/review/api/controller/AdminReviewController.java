package com.ltz.review.api.controller;

import com.ltz.review.api.dto.request.CloseReportsRequest;
import com.ltz.review.api.dto.request.ModerateReviewRequest;
import com.ltz.review.api.dto.response.AdminReportResponse;
import com.ltz.review.api.dto.response.AdminReportedReviewPageResponse;
import com.ltz.review.api.dto.response.AdminReviewResponse;
import com.ltz.review.api.dto.response.CloseReportsResponse;
import com.ltz.review.application.service.ReviewModerationService;
import com.ltz.review.domain.enums.ReportStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Moderatör uçları (yalnız MODERATOR/ADMIN kullanıcı JWT'si; kural güvenlik zincirindedir). Aktör yalnız token sub'ıdır. */
@RestController
@RequestMapping("/api/v1/review/admin")
public class AdminReviewController {
    private final ReviewModerationService service;

    public AdminReviewController(ReviewModerationService service) { this.service = service; }

    @GetMapping("/reports")
    public AdminReportedReviewPageResponse reported(@RequestParam(defaultValue = "RECEIVED") ReportStatus status,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return service.reportedReviews(status, page, size);
    }

    @GetMapping("/reviews/{reviewId}")
    public AdminReviewResponse review(@PathVariable UUID reviewId) { return service.review(reviewId); }

    @GetMapping("/reviews/{reviewId}/reports")
    public List<AdminReportResponse> reports(@PathVariable UUID reviewId) { return service.reports(reviewId); }

    @PostMapping("/reviews/{reviewId}/moderation")
    public AdminReviewResponse moderate(@PathVariable UUID reviewId, @AuthenticationPrincipal Jwt actor, @Valid @RequestBody ModerateReviewRequest request) {
        return service.moderate(UUID.fromString(actor.getSubject()), reviewId, request.action(), request.reason(), request.version());
    }

    @PostMapping("/reviews/{reviewId}/reports/close")
    public CloseReportsResponse close(@PathVariable UUID reviewId, @AuthenticationPrincipal Jwt actor, @Valid @RequestBody CloseReportsRequest request) {
        return new CloseReportsResponse(service.closeReports(UUID.fromString(actor.getSubject()), reviewId, request.outcome(), request.reason()));
    }
}
