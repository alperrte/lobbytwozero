package com.ltz.performance.api.controller;

import com.ltz.performance.api.dto.response.SyncConsentResponse;
import com.ltz.performance.application.port.CurrentActor;
import com.ltz.performance.application.service.SyncConsentService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/performance/sync-consent")
public class SyncConsentController {
    private final SyncConsentService consents;
    private final CurrentActor actor;

    public SyncConsentController(SyncConsentService consents, CurrentActor actor) {
        this.consents = consents;
        this.actor = actor;
    }

    @GetMapping
    @Operation(summary = "PERF-17: Kullanıcının senkronizasyon izni; kayıt yoksa izin verilmemiştir")
    public ResponseEntity<SyncConsentResponse> get() {
        return PerformanceControllerSupport.privateResponse(consents.get(actor.requireUserId()));
    }

    @PutMapping
    @Operation(summary = "PERF-17: Açık izin verir; idempotent")
    public ResponseEntity<SyncConsentResponse> grant(HttpServletRequest servlet) {
        return PerformanceControllerSupport.privateResponse(
                consents.grant(actor.requireUserId(), servlet.getRemoteAddr()));
    }

    @DeleteMapping
    @Operation(summary = "PERF-17: İzni iptal eder; sonraki senkronizasyonlar reddedilir, idempotent")
    public ResponseEntity<SyncConsentResponse> revoke(HttpServletRequest servlet) {
        return PerformanceControllerSupport.privateResponse(
                consents.revoke(actor.requireUserId(), servlet.getRemoteAddr()));
    }
}
