package com.ltz.library.api.controller;

import com.ltz.library.api.dto.response.LibraryCollectionPageResponse;
import com.ltz.library.api.dto.response.LibraryStatisticsResponse;
import com.ltz.library.application.service.LibrarySharedReadService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/library/users/{userId}/entries")
public class LibraryProfileController {
    private final LibrarySharedReadService reads;
    public LibraryProfileController(LibrarySharedReadService reads) { this.reads = reads; }

    @GetMapping
    public ResponseEntity<LibraryCollectionPageResponse> list(@PathVariable UUID userId, @AuthenticationPrincipal Jwt token,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reads.profile(userId, UUID.fromString(token.getSubject()), page, size));
    }

    @GetMapping("/statistics")
    public ResponseEntity<LibraryStatisticsResponse> statistics(@PathVariable UUID userId, @AuthenticationPrincipal Jwt token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reads.statistics(userId, UUID.fromString(token.getSubject())));
    }
}
