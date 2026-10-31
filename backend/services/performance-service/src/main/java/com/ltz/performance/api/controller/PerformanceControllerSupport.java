package com.ltz.performance.api.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

final class PerformanceControllerSupport {
    private PerformanceControllerSupport() { }

    /** Kişisel veri içeren yanıtlar önbelleğe alınmaz. */
    static <T> ResponseEntity<T> privateResponse(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(body);
    }

    static <T> ResponseEntity<T> createdResponse(T body) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore().cachePrivate()).body(body);
    }

    static ResponseEntity<Void> noContent() {
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore().cachePrivate()).build();
    }
}
