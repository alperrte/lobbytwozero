package com.ltz.hardware.api.controller;

import com.ltz.hardware.api.dto.request.*;
import com.ltz.hardware.api.dto.response.*;
import com.ltz.hardware.application.service.*;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/hardware/profiles/{profileId}")
public class HardwareSyncController {
    private final HardwareSyncConsentService consents;
    private final HardwareSyncService sync;
    public HardwareSyncController(HardwareSyncConsentService consents, HardwareSyncService sync) {
        this.consents = consents; this.sync = sync;
    }

    @GetMapping("/sync-consent")
    @Operation(summary = "HW-07: Owner izin ve son sync metadata'sı; sürekli izin varsayılan kapalı")
    public ResponseEntity<HardwareSyncConsentResponse> get(@PathVariable UUID profileId) {
        return privateResponse(consents.get(profileId));
    }

    @PutMapping("/sync-consent")
    @Operation(summary = "HW-07: Owner açık onayı ve doğrulanmış aktif cihaz için yeni izin generation'ı")
    public ResponseEntity<HardwareSyncConsentResponse> grant(@PathVariable UUID profileId,
            @Valid @RequestBody GrantHardwareSyncRequest request, HttpServletRequest servlet) {
        return privateResponse(consents.grant(profileId, request, servlet.getRemoteAddr()));
    }

    @DeleteMapping("/sync-consent")
    @Operation(summary = "HW-07: Owner izni iptal eder; hardware korunur, sonraki apply/retry reddedilir")
    public ResponseEntity<HardwareSyncConsentResponse> revoke(@PathVariable UUID profileId,
            @RequestParam @PositiveOrZero long profileVersion, HttpServletRequest servlet) {
        return privateResponse(consents.revoke(profileId, profileVersion, servlet.getRemoteAddr()));
    }

    @PostMapping("/sync/snapshots")
    @Operation(summary = "HW-07: Yalnız doğrulanmış cihaz + aktif yerel izin; idempotent tam otomatik küme")
    public ResponseEntity<HardwareSyncReceiptResponse> apply(@PathVariable UUID profileId,
            @RequestHeader("Idempotency-Key") UUID key, @Valid @RequestBody ApplyHardwareSyncRequest request,
            HttpServletRequest servlet) {
        return privateResponse(sync.apply(profileId, key, request, servlet.getRemoteAddr()));
    }

    private <T> ResponseEntity<T> privateResponse(T response) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(response);
    }
}
