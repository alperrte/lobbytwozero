package com.ltz.performance.api.controller;

import com.ltz.performance.application.service.DeviceActiveCheckService;
import com.ltz.performance.application.service.PerformanceAccountErasureService;
import com.ltz.performance.domain.exception.PerformanceNotFoundException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Servisler arası uçlar; Gateway dışarı açmaz, yalnız svc+jwt ile ve token'ın hesap bağlamasıyla çalışır. */
@RestController
public class PerformanceInternalController {
    private static final String AUTH_SUBJECT = "svc:auth-service";
    private static final String HARDWARE_SUBJECT = "svc:hardware-service";

    private final PerformanceAccountErasureService erasure;
    private final DeviceActiveCheckService deviceCheck;

    public PerformanceInternalController(PerformanceAccountErasureService erasure,
            DeviceActiveCheckService deviceCheck) {
        this.erasure = erasure;
        this.deviceCheck = deviceCheck;
    }

    @DeleteMapping("/internal/v1/accounts/{accountId}")
    public ResponseEntity<Object> erase(@PathVariable UUID accountId, @AuthenticationPrincipal Jwt jwt) {
        requireBinding(jwt, AUTH_SUBJECT, accountId);
        if (!erasure.erase(accountId)) {
            // Sınırlı parti işlendi; tamamlanmamış silme asla onaylanmaz, Auth yeniden dener.
            var problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                    "Hesap temizliği devam ediyor; tekrar deneyin.");
            problem.setProperty("code", "ACCOUNT_ERASURE_PENDING");
            return ResponseEntity.status(503).header("Cache-Control", "private, no-store")
                    .header("Retry-After", "60").body(problem);
        }
        return ResponseEntity.noContent().header("Cache-Control", "private, no-store").build();
    }

    /** Hardware'in sync cihaz politikası için: cihaz hesaba ait ve Performance'ta aktifse 204, değilse 404. */
    @GetMapping("/internal/v1/accounts/{accountId}/devices/{deviceId}")
    public ResponseEntity<Void> activeDevice(@PathVariable UUID accountId, @PathVariable UUID deviceId,
            @AuthenticationPrincipal Jwt jwt) {
        requireBinding(jwt, HARDWARE_SUBJECT, accountId);
        if (!deviceCheck.isActiveOwnedDevice(accountId, deviceId)) throw new PerformanceNotFoundException();
        return ResponseEntity.noContent().header("Cache-Control", "private, no-store").build();
    }

    private static void requireBinding(Jwt jwt, String subject, UUID accountId) {
        if (jwt == null || !subject.equals(jwt.getSubject())
                || !accountId.toString().equals(jwt.getClaims().get("account_id"))) {
            throw new AccessDeniedException("Account binding required");
        }
    }
}
