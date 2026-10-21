package com.ltz.hardware.api.controller;

import com.ltz.hardware.api.dto.request.UpdateHardwarePrivacyRequest;
import com.ltz.hardware.api.dto.response.*;
import com.ltz.hardware.application.service.HardwarePrivacyService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/hardware/profiles/{profileId}")
public class HardwarePrivacyController {
    private final HardwarePrivacyService service;

    public HardwarePrivacyController(HardwarePrivacyService service) { this.service = service; }

    @GetMapping("/privacy")
    @Operation(summary = "HW-08: Owner gizlilik kurallarını okur; eksik kurallar PRIVATE")
    public ResponseEntity<HardwarePrivacyResponse> get(@PathVariable UUID profileId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.get(profileId));
    }

    @PutMapping("/privacy")
    @Operation(summary = "HW-08: Owner gizlilik kurallarını aggregate sürümüyle değiştirir")
    public ResponseEntity<HardwarePrivacyResponse> update(@PathVariable UUID profileId,
            @Valid @RequestBody UpdateHardwarePrivacyRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.update(profileId, request));
    }

    @GetMapping("/shared")
    @Operation(summary = "HW-08: Yerel kurallar ve güvenilir dış kararla filtrelenmiş paylaşım")
    public ResponseEntity<SharedHardwareProfileResponse> shared(@PathVariable UUID profileId,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.shared(profileId, page, size));
    }
}
