package com.ltz.hardware.api.controller;

import com.ltz.hardware.api.dto.request.CreateHardwareReviewReportRequest;
import com.ltz.hardware.api.dto.response.HardwareReviewReportResponse;
import com.ltz.hardware.application.service.HardwareReviewReportService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/hardware")
public class HardwareReviewReportController {
    private final HardwareReviewReportService service;
    public HardwareReviewReportController(HardwareReviewReportService service) { this.service = service; }

    @PostMapping("/reviews/{reviewId}/reports")
    @Operation(summary = "HW-13: Görülebilen yabancı yorum için tekil rapor ve PENDING outbox kaydı")
    public ResponseEntity<HardwareReviewReportResponse> create(@PathVariable UUID reviewId,
            @Valid @RequestBody CreateHardwareReviewReportRequest request, HttpServletRequest servlet) {
        var result = service.create(reviewId, request, servlet.getRemoteAddr());
        return ResponseEntity.created(URI.create("/api/v1/hardware/review-reports/" + result.id()))
                .cacheControl(CacheControl.noStore().cachePrivate()).body(result);
    }

    @GetMapping("/review-reports/{reportId}")
    @Operation(summary = "HW-13: Yalnız reporter'ın kendi raporu; gerçek teslim doğrulanmış sayılmaz")
    public ResponseEntity<HardwareReviewReportResponse> get(@PathVariable UUID reportId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.get(reportId));
    }
}
