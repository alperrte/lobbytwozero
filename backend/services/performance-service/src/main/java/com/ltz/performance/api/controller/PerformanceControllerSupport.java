package com.ltz.performance.api.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;

final class PerformanceControllerSupport {
    private PerformanceControllerSupport() { }

    /** Kişisel veri içeren yanıtlar önbelleğe alınmaz. */
    static <T> ResponseEntity<T> privateResponse(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(body);
    }
}
