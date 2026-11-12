package com.ltz.hardware.api.controller;

import com.ltz.hardware.api.dto.request.ApplyHardwareSyncRequest;
import com.ltz.hardware.api.dto.request.GrantHardwareSyncRequest;
import com.ltz.hardware.api.dto.response.HardwareSyncConsentResponse;
import com.ltz.hardware.api.dto.response.HardwareSyncReceiptResponse;
import com.ltz.hardware.application.service.HardwareSyncConsentService;
import com.ltz.hardware.application.service.HardwareSyncService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
