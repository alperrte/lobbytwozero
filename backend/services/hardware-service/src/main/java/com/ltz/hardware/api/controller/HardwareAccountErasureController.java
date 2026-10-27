package com.ltz.hardware.api.controller;

import com.ltz.hardware.application.service.HardwareAccountErasureService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HardwareAccountErasureController {
    private final HardwareAccountErasureService service;
    public HardwareAccountErasureController(HardwareAccountErasureService service) { this.service = service; }

    @DeleteMapping("/internal/v1/accounts/{accountId}")
    public ResponseEntity<Object> erase(@PathVariable UUID accountId, @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null || !"svc:auth-service".equals(jwt.getSubject())
                || !accountId.toString().equals(jwt.getClaims().get("account_id"))) {
            throw new AccessDeniedException("Account erasure binding required");
        }
        if (!service.erase(accountId)) {
            // The bounded batch committed. Auth retries forward; never acknowledge unfinished erasure.
            var problem = org.springframework.http.ProblemDetail.forStatusAndDetail(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Hesap temizliği devam ediyor; tekrar deneyin.");
            problem.setProperty("code", "ACCOUNT_ERASURE_PENDING");
            return ResponseEntity.status(503).header("Cache-Control", "private, no-store")
                    .header("Retry-After", "60").body(problem);
        }
        return ResponseEntity.noContent().header("Cache-Control", "private, no-store").build();
    }
}
