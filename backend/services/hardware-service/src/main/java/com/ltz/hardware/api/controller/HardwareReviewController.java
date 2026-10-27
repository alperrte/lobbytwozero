package com.ltz.hardware.api.controller;

import com.ltz.hardware.api.dto.request.*;
import com.ltz.hardware.api.dto.response.*;
import com.ltz.hardware.application.service.HardwareReviewService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/hardware")
public class HardwareReviewController {
    private final HardwareReviewService service;
    public HardwareReviewController(HardwareReviewService service) { this.service = service; }

    @PostMapping("/catalog/products/{productId}/reviews")
    @Operation(summary = "HW-12: Aktif genel ürüne owner yorumu ekler")
    public ResponseEntity<HardwareReviewResponse> create(@PathVariable UUID productId,
            @Valid @RequestBody CreateHardwareReviewRequest request, HttpServletRequest servlet) {
        var result = service.create(productId, request, servlet.getRemoteAddr());
        return ResponseEntity.created(URI.create("/api/v1/hardware/reviews/" + result.id()))
                .cacheControl(CacheControl.noStore().cachePrivate()).body(result);
    }

    @GetMapping("/catalog/products/{productId}/reviews")
    @Operation(summary = "HW-12: Görünürlük/engel filtresinden sonra yorumları ve puan özetini sayfalar")
    public ResponseEntity<HardwareReviewPageResponse> list(@PathVariable UUID productId,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.list(productId, page, size));
    }

    @GetMapping("/reviews/mine")
    @Operation(summary = "HW-12: Yalnız owner'ın silinmemiş yorumlarını sayfalar")
    public ResponseEntity<HardwareOwnReviewPageResponse> mine(
            @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.mine(page, size));
    }

    @GetMapping("/reviews/{reviewId}")
    @Operation(summary = "HW-12: Owner veya güvenilir audience kararıyla erişilebilir yorum detayı")
    public ResponseEntity<HardwareReviewResponse> detail(@PathVariable UUID reviewId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.detail(reviewId));
    }

    @PutMapping("/reviews/{reviewId}")
    @Operation(summary = "HW-12: Owner yalnız VISIBLE yorumunu sürümle günceller")
    public ResponseEntity<HardwareReviewResponse> update(@PathVariable UUID reviewId,
            @Valid @RequestBody UpdateHardwareReviewRequest request, HttpServletRequest servlet) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                .body(service.update(reviewId, request, servlet.getRemoteAddr()));
    }

    @DeleteMapping("/reviews/{reviewId}")
    @Operation(summary = "HW-12: Owner yorumunu soft-delete eder ve metin/puanı temizler")
    public ResponseEntity<Void> delete(@PathVariable UUID reviewId, @RequestParam @PositiveOrZero long reviewVersion,
            HttpServletRequest servlet) {
        service.delete(reviewId, reviewVersion, servlet.getRemoteAddr());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore().cachePrivate()).build();
    }
}
