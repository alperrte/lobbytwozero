package com.ltz.hardware.api.controller;

import com.ltz.hardware.api.dto.request.PreviewHardwareScanRequest;
import com.ltz.hardware.api.dto.response.HardwareScanPreviewResponse;
import com.ltz.hardware.application.service.HardwareScanService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/hardware/profiles/{profileId}/sync/preview")
public class HardwareScanController {
    private final HardwareScanService service;
    public HardwareScanController(HardwareScanService service) { this.service = service; }

    @PostMapping
    @Operation(summary = "HW-06: Açık onaylı owner tarama önizlemesi; donanım kaydetmez ve cihaz doğrulamaz")
    public ResponseEntity<HardwareScanPreviewResponse> preview(@PathVariable UUID profileId,
            @Valid @RequestBody PreviewHardwareScanRequest request, HttpServletRequest servlet) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                .body(service.preview(profileId, request, servlet.getRemoteAddr()));
    }
}
