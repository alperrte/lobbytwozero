package com.ltz.performance.application.service;

import java.time.Instant;

/**
 * Yarı açık zaman aralığı {@code [from, to)}; iki uç da isteğe bağlıdır. Anlamsız (ters/boş) ve makul olmayan
 * uçlar reddedilir ki veritabanına taşma/aşırı geniş tarama gitmesin.
 */
public record TimeWindow(Instant from, Instant to) {
    static final Instant EARLIEST = Instant.parse("2000-01-01T00:00:00Z");
    static final Instant LATEST = Instant.parse("2200-01-01T00:00:00Z");
    static final TimeWindow UNBOUNDED = new TimeWindow(null, null);

    public TimeWindow {
        requireBound(from);
        requireBound(to);
        if (from != null && to != null && !from.isBefore(to)) throw new IllegalArgumentException("from must precede to");
    }

    private static void requireBound(Instant value) {
        if (value != null && (value.isBefore(EARLIEST) || value.isAfter(LATEST))) {
            throw new IllegalArgumentException("time bound out of range");
        }
    }
}
