package com.ltz.hardware.api.controller;

import com.ltz.hardware.api.dto.request.ModerateHardwareReviewRequest;
import com.ltz.hardware.api.dto.response.*;
import com.ltz.hardware.application.service.HardwareReviewModerationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/hardware/moderation/reviews/{reviewId}")
public class HardwareReviewModerationController {
    private final HardwareReviewModerationService service;
    public HardwareReviewModerationController(HardwareReviewModerationService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "HW-13: Yetkili moderatör için kaynak hedefi ve sayfalı raporlar")
    public ResponseEntity<HardwareReviewModerationDetailResponse> detail(@PathVariable UUID reviewId,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.detail(reviewId, page, size));
    }

    @PostMapping("/actions")
    @Operation(summary = "HW-13: Gerekçeli/version kontrollü HIDE veya RESTORE; audit ve PENDING notice atomik")
    public ResponseEntity<HardwareReviewModerationResponse> apply(@PathVariable UUID reviewId,
            @Valid @RequestBody ModerateHardwareReviewRequest request, HttpServletRequest servlet) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                .body(service.apply(reviewId, request, servlet.getRemoteAddr()));
    }
}
