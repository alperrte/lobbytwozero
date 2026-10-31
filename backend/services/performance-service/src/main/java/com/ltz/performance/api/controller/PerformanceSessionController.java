package com.ltz.performance.api.controller;

import com.ltz.performance.api.dto.request.EndSessionRequest;
import com.ltz.performance.api.dto.request.ReportRequest;
import com.ltz.performance.api.dto.request.StartSessionRequest;
import com.ltz.performance.api.dto.response.DeletedSessionsResponse;
import com.ltz.performance.api.dto.response.ReportResponse;
import com.ltz.performance.api.dto.response.SessionPageResponse;
import com.ltz.performance.api.dto.response.SessionResponse;
import com.ltz.performance.application.port.CurrentActor;
import com.ltz.performance.application.service.PerformanceReportService;
import com.ltz.performance.application.service.PerformanceSessionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
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
@RequestMapping("/api/v1/performance/sessions")
public class PerformanceSessionController {
    private final PerformanceSessionService sessions;
    private final PerformanceReportService reports;
    private final CurrentActor actor;

    public PerformanceSessionController(PerformanceSessionService sessions, PerformanceReportService reports,
            CurrentActor actor) {
        this.sessions = sessions;
        this.reports = reports;
        this.actor = actor;
    }

    @PostMapping
    @Operation(summary = "PERF-03: Elle performans oturumu başlatır; oyun ve donanım profili sözleşmeyle doğrulanır")
    public ResponseEntity<SessionResponse> start(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody StartSessionRequest request, HttpServletRequest servlet) {
        return PerformanceControllerSupport.createdResponse(
                sessions.start(actor.requireUserId(), authorization, request, servlet.getRemoteAddr()));
    }

    @GetMapping
    @Operation(summary = "PERF-03: Çağıranın oturumları, en yeniden eskiye")
    public ResponseEntity<SessionPageResponse> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PerformanceControllerSupport.privateResponse(sessions.list(actor.requireUserId(), page, size));
    }

    @GetMapping("/active")
    @Operation(summary = "PERF-03: Çağıranın aktif oturumu; yoksa 404")
    public ResponseEntity<SessionResponse> active() {
        return PerformanceControllerSupport.privateResponse(sessions.active(actor.requireUserId()));
    }

    @GetMapping("/{sessionId}")
    @Operation(summary = "PERF-03: Çağıranın tek oturumu")
    public ResponseEntity<SessionResponse> get(@PathVariable UUID sessionId) {
        return PerformanceControllerSupport.privateResponse(sessions.get(actor.requireUserId(), sessionId));
    }

    @PostMapping("/{sessionId}/end")
    @Operation(summary = "PERF-04: Oturumu bitirir (COMPLETED veya ABANDONED); aynı durum tekrarı idempotent")
    public ResponseEntity<SessionResponse> end(@PathVariable UUID sessionId,
            @Valid @RequestBody(required = false) EndSessionRequest request, HttpServletRequest servlet) {
        var target = request == null ? new EndSessionRequest(null) : request;
        return PerformanceControllerSupport.privateResponse(sessions.end(actor.requireUserId(), sessionId,
                target.targetOrDefault(), servlet.getRemoteAddr()));
    }

    @DeleteMapping("/{sessionId}")
    @Operation(summary = "PERF-20: Oturumu ve raporunu siler; yalnız sahibi")
    public ResponseEntity<Void> delete(@PathVariable UUID sessionId, HttpServletRequest servlet) {
        sessions.delete(actor.requireUserId(), sessionId, servlet.getRemoteAddr());
        return PerformanceControllerSupport.noContent();
    }

    @DeleteMapping
    @Operation(summary = "PERF-20: Çağıranın tüm oturum ve raporlarını siler; confirm=true zorunludur")
    public ResponseEntity<DeletedSessionsResponse> deleteAll(@RequestParam boolean confirm,
            HttpServletRequest servlet) {
        if (!confirm) throw new IllegalArgumentException("confirm=true is required");
        return PerformanceControllerSupport.privateResponse(
                sessions.deleteAll(actor.requireUserId(), servlet.getRemoteAddr()));
    }

    @PutMapping("/{sessionId}/report")
    @Operation(summary = "PERF-13: Tamamlanmış oturum için elle rapor yazar; ilk yazım 201, güncelleme 200")
    public ResponseEntity<ReportResponse> putReport(@PathVariable UUID sessionId,
            @Valid @RequestBody ReportRequest request, HttpServletRequest servlet) {
        var result = reports.put(actor.requireUserId(), sessionId, request, servlet.getRemoteAddr());
        return result.created() ? PerformanceControllerSupport.createdResponse(result.report())
                : PerformanceControllerSupport.privateResponse(result.report());
    }

    @GetMapping("/{sessionId}/report")
    @Operation(summary = "PERF-13: Oturumun raporu; yoksa 404")
    public ResponseEntity<ReportResponse> getReport(@PathVariable UUID sessionId) {
        return PerformanceControllerSupport.privateResponse(reports.get(actor.requireUserId(), sessionId));
    }

    @DeleteMapping("/{sessionId}/report")
    @Operation(summary = "PERF-20: Yalnız raporu siler; oturum korunur")
    public ResponseEntity<Void> deleteReport(@PathVariable UUID sessionId, HttpServletRequest servlet) {
        reports.delete(actor.requireUserId(), sessionId, servlet.getRemoteAddr());
        return PerformanceControllerSupport.noContent();
    }
}
