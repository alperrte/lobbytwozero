package com.ltz.library.api.controller;

import com.ltz.library.api.dto.response.LibraryCollectionPageResponse;
import com.ltz.library.api.dto.response.LibraryStatisticsResponse;
import com.ltz.library.api.dto.response.LibrarySignalPageResponse;
import com.ltz.library.application.service.LibrarySharedReadService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Taslak servis okuma sözleşmesi: Auth istemci/delegasyon desteği henüz yoktur. */
@RestController
@RequestMapping("/internal/v1/library/users/{userId}")
public class LibraryInternalReadController {
    private final LibrarySharedReadService reads;
    public LibraryInternalReadController(LibrarySharedReadService reads) { this.reads = reads; }

    @GetMapping("/entries")
    public ResponseEntity<LibraryCollectionPageResponse> profile(@PathVariable UUID userId, @AuthenticationPrincipal Jwt token,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        bound(token, userId, "svc:user-service");
        return response(reads.profile(userId, viewer(token), page, size));
    }

    @GetMapping("/entries/statistics")
    public ResponseEntity<LibraryStatisticsResponse> statistics(@PathVariable UUID userId, @AuthenticationPrincipal Jwt token) {
        bound(token, userId, "svc:user-service");
        return response(reads.statistics(userId, viewer(token)));
    }

    @GetMapping("/recommendation-signals")
    public ResponseEntity<LibrarySignalPageResponse> signals(@PathVariable UUID userId, @AuthenticationPrincipal Jwt token,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        bound(token, userId, "svc:recommendation-service");
        return response(reads.signals(userId, page, size));
    }

    private static void bound(Jwt token, UUID target, String subject) {
        if (!subject.equals(token.getSubject()) || !target.toString().equals(token.getClaims().get("target_user_id")))
            throw new AccessDeniedException("Servis okuma yetkisi geçersiz.");
    }

    private static UUID viewer(Jwt token) {
        Object claim = token.getClaims().get("viewer_id");
        if (claim instanceof String value) {
            try {
                UUID id = UUID.fromString(value);
                if (id.toString().equals(value)) return id;
            } catch (IllegalArgumentException ignored) { /* ret */ }
        }
        throw new AccessDeniedException("Doğrulanmış görüntüleyen zorunludur.");
    }

    private static <T> ResponseEntity<T> response(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}
