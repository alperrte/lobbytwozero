package com.ltz.performance.api.controller;

import com.ltz.performance.api.dto.response.PerformanceReferenceResponse;
import com.ltz.performance.api.dto.response.PerformanceSummaryResponse;
import com.ltz.performance.application.service.DeviceActiveCheckService;
import com.ltz.performance.application.service.PerformanceAccountErasureService;
import com.ltz.performance.application.service.PerformanceHistoryService;
import com.ltz.performance.domain.exception.PerformanceNotFoundException;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Servisler arası uçlar; Gateway dışarı açmaz, yalnız svc+jwt ile ve token'ın hesap bağlamasıyla çalışır. */
@RestController
public class PerformanceInternalController {
    private static final String AUTH_SUBJECT = "svc:auth-service";
    private static final String HARDWARE_SUBJECT = "svc:hardware-service";
    private static final String RECOMMENDATION_SUBJECT = "svc:recommendation-service";
    private static final String NO_STORE = "private, no-store";

    private final PerformanceAccountErasureService erasure;
    private final DeviceActiveCheckService deviceCheck;
    private final PerformanceHistoryService history;

    public PerformanceInternalController(PerformanceAccountErasureService erasure,
            DeviceActiveCheckService deviceCheck, PerformanceHistoryService history) {
        this.erasure = erasure;
        this.deviceCheck = deviceCheck;
        this.history = history;
    }

    @DeleteMapping("/internal/v1/accounts/{accountId}")
    public ResponseEntity<Object> erase(@PathVariable UUID accountId, @AuthenticationPrincipal Jwt jwt) {
        requireBinding(jwt, AUTH_SUBJECT, accountId);
        if (!erasure.erase(accountId)) {
            // Sınırlı parti işlendi; tamamlanmamış silme asla onaylanmaz, Auth yeniden dener.
            var problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                    "Hesap temizliği devam ediyor; tekrar deneyin.");
            problem.setProperty("code", "ACCOUNT_ERASURE_PENDING");
            return ResponseEntity.status(503).header(HttpHeaders.CACHE_CONTROL, NO_STORE)
                    .header("Retry-After", "60").body(problem);
        }
        return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, NO_STORE).build();
    }

    /** Hardware'in sync cihaz politikası için: cihaz hesaba ait ve Performance'ta aktifse 204, değilse 404. */
    @GetMapping("/internal/v1/accounts/{accountId}/devices/{deviceId}")
    public ResponseEntity<Void> activeDevice(@PathVariable UUID accountId, @PathVariable UUID deviceId,
            @AuthenticationPrincipal Jwt jwt) {
        requireBinding(jwt, HARDWARE_SUBJECT, accountId);
        if (!deviceCheck.isActiveOwnedDevice(accountId, deviceId)) throw new PerformanceNotFoundException();
        return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, NO_STORE).build();
    }

    /**
     * Hardware sözleşmesi (PERF-21): hesabın bir donanım profiliyle tamamlanmış oturumlarının metrik özeti.
     * Oturum/oyun/cihaz kimliği veya başlık döndürmez; başka hesabın verisi karışmaz.
     */
    @GetMapping("/internal/v1/accounts/{accountId}/performance-summary")
    public ResponseEntity<PerformanceSummaryResponse> performanceSummary(@PathVariable UUID accountId,
            @RequestParam UUID hardwareProfileId, @AuthenticationPrincipal Jwt jwt) {
        requireBinding(jwt, HARDWARE_SUBJECT, accountId);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, NO_STORE)
                .body(history.hardwareSummary(accountId, hardwareProfileId));
    }

    /**
     * Recommendation sözleşmesi (PERF-21): hesabın bir oyundaki güvenilir FPS referansı. Yalnız FPS; sıcaklık,
     * kullanım veya oturum ayrıntısı içermez. Yetersiz veride {@code reliable=false}.
     */
    @GetMapping("/internal/v1/accounts/{accountId}/games/{gameId}/performance-reference")
    public ResponseEntity<PerformanceReferenceResponse> performanceReference(@PathVariable UUID accountId,
            @PathVariable UUID gameId, @RequestParam(required = false) UUID hardwareProfileId,
            @AuthenticationPrincipal Jwt jwt) {
        requireBinding(jwt, RECOMMENDATION_SUBJECT, accountId);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, NO_STORE)
                .body(history.gameReference(accountId, gameId, hardwareProfileId));
    }

    private static void requireBinding(Jwt jwt, String subject, UUID accountId) {
        if (jwt == null || !subject.equals(jwt.getSubject())
                || !accountId.toString().equals(jwt.getClaims().get("account_id"))) {
            throw new AccessDeniedException("Account binding required");
        }
    }
}
