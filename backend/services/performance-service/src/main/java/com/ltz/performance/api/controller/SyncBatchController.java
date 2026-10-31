package com.ltz.performance.api.controller;

import com.ltz.performance.api.dto.request.SyncBatchRequest;
import com.ltz.performance.api.dto.response.SyncBatchResponse;
import com.ltz.performance.application.port.PerformanceSyncDeviceAccess;
import com.ltz.performance.application.service.SyncBatchService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/performance/sync/batches")
public class SyncBatchController {
    private final SyncBatchService sync;
    private final PerformanceSyncDeviceAccess deviceAccess;

    public SyncBatchController(SyncBatchService sync, PerformanceSyncDeviceAccess deviceAccess) {
        this.sync = sync;
        this.deviceAccess = deviceAccess;
    }

    @PostMapping
    @Operation(summary = "PERF-17/18: Cihaz token'ı + aktif izin + bağlı cihaz; Idempotency-Key ile tekrarsız kabul")
    public ResponseEntity<SyncBatchResponse> accept(@RequestHeader("Idempotency-Key") UUID key,
            @Valid @RequestBody SyncBatchRequest request, HttpServletRequest servlet) {
        return PerformanceControllerSupport.privateResponse(
                sync.accept(deviceAccess.requireDevice(), key, request, servlet.getRemoteAddr()));
    }
}
