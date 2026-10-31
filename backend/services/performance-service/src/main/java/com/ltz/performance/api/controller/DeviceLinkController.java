package com.ltz.performance.api.controller;

import com.ltz.performance.api.dto.request.LinkDeviceRequest;
import com.ltz.performance.api.dto.response.DeviceLinkResponse;
import com.ltz.performance.application.port.CurrentActor;
import com.ltz.performance.application.service.DeviceLinkService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/performance/devices")
public class DeviceLinkController {
    private final DeviceLinkService devices;
    private final CurrentActor actor;

    public DeviceLinkController(DeviceLinkService devices, CurrentActor actor) {
        this.devices = devices;
        this.actor = actor;
    }

    @GetMapping
    @Operation(summary = "PERF-01: Çağıranın Performance'a bağladığı cihazlar")
    public ResponseEntity<List<DeviceLinkResponse>> list() {
        return PerformanceControllerSupport.privateResponse(devices.list(actor.requireUserId()));
    }

    @PostMapping
    @Operation(summary = "PERF-01: Auth'ta kullanılabilir kendi cihazını bağlar; sahiplik Auth listesinden doğrulanır")
    public ResponseEntity<DeviceLinkResponse> link(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody LinkDeviceRequest request, HttpServletRequest servlet) {
        return PerformanceControllerSupport.privateResponse(
                devices.link(actor.requireUserId(), authorization, request.deviceId(), servlet.getRemoteAddr()));
    }

    @DeleteMapping("/{linkId}")
    @Operation(summary = "PERF-01: Bağlantıyı devre dışı bırakır; kayıt silinmez, idempotent")
    public ResponseEntity<DeviceLinkResponse> disable(@PathVariable UUID linkId, HttpServletRequest servlet) {
        return PerformanceControllerSupport.privateResponse(
                devices.disable(actor.requireUserId(), linkId, servlet.getRemoteAddr()));
    }
}
