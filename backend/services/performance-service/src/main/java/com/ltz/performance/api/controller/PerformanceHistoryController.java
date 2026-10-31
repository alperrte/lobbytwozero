package com.ltz.performance.api.controller;

import com.ltz.performance.api.dto.response.AccountStatsResponse;
import com.ltz.performance.api.dto.response.SamplePageResponse;
import com.ltz.performance.api.dto.response.SeriesResponse;
import com.ltz.performance.api.dto.response.SessionStatsResponse;
import com.ltz.performance.application.port.CurrentActor;
import com.ltz.performance.application.service.PerformanceHistoryService;
import com.ltz.performance.application.service.SessionFilter;
import com.ltz.performance.application.service.TimeWindow;
import com.ltz.performance.domain.enums.SessionStatus;
import io.swagger.v3.oas.annotations.Operation;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Ölçüm geçmişi, istatistik ve grafik okumaları; hepsi yalnız çağıranın verisini döner. */
@RestController
@RequestMapping("/api/v1/performance")
public class PerformanceHistoryController {
    private final PerformanceHistoryService history;
    private final CurrentActor actor;

    public PerformanceHistoryController(PerformanceHistoryService history, CurrentActor actor) {
        this.history = history;
        this.actor = actor;
    }

    @GetMapping("/sessions/{sessionId}/samples")
    @Operation(summary = "PERF-14/19: Oturumun ham örnekleri; metrik ve zaman filtresi, sabit sıra (captured_at, metric, id)")
    public ResponseEntity<SamplePageResponse> samples(@PathVariable UUID sessionId,
            @RequestParam(required = false) String metric,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PerformanceHistoryService.DEFAULT_SAMPLE_PAGE) int size) {
        return PerformanceControllerSupport.privateResponse(history.samples(actor.requireUserId(), sessionId,
                PerformanceHistoryService.parseMetric(metric), new TimeWindow(from, to), page, size));
    }

    @GetMapping("/sessions/{sessionId}/stats")
    @Operation(summary = "PERF-15: Oturumun metrik başına ortalama/min/max/sayı özeti; örneksiz metrik listelenmez")
    public ResponseEntity<SessionStatsResponse> sessionStats(@PathVariable UUID sessionId,
            @RequestParam(required = false) String metric,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return PerformanceControllerSupport.privateResponse(history.sessionStats(actor.requireUserId(), sessionId,
                PerformanceHistoryService.parseMetric(metric), new TimeWindow(from, to)));
    }

    @GetMapping("/sessions/{sessionId}/series")
    @Operation(summary = "PERF-16: Tek metrik için zaman serisi; çok veri açıkça belirtilen kova ortalamasına indirgenir")
    public ResponseEntity<SeriesResponse> series(@PathVariable UUID sessionId, @RequestParam String metric,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "" + PerformanceHistoryService.DEFAULT_POINTS) int maxPoints) {
        return PerformanceControllerSupport.privateResponse(history.series(actor.requireUserId(), sessionId,
                PerformanceHistoryService.parseMetric(metric), new TimeWindow(from, to), maxPoints));
    }

    @GetMapping("/stats")
    @Operation(summary = "PERF-15/19: Filtreye uyan tüm oturumların metrik özeti (örnek ağırlıklı)")
    public ResponseEntity<AccountStatsResponse> accountStats(@RequestParam(required = false) UUID gameId,
            @RequestParam(required = false) UUID hardwareProfileId, @RequestParam(required = false) UUID deviceLinkId,
            @RequestParam(required = false) SessionStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startedTo,
            @RequestParam(required = false) String metric) {
        var filter = new SessionFilter(gameId, hardwareProfileId, deviceLinkId, status,
                new TimeWindow(startedFrom, startedTo));
        return PerformanceControllerSupport.privateResponse(history.accountStats(actor.requireUserId(), filter,
                PerformanceHistoryService.parseMetric(metric)));
    }
}
