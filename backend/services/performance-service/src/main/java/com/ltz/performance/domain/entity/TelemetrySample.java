package com.ltz.performance.domain.entity;

import com.ltz.performance.domain.enums.SampleMetric;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Doğrulanmamış tek metrik okuması. Zaman milisaniyeye indirilir; böylece benzersizlik anahtarı kararlıdır. */
public record TelemetrySample(Instant capturedAt, SampleMetric metric, double value) {
    public TelemetrySample {
        Objects.requireNonNull(capturedAt);
        Objects.requireNonNull(metric);
        capturedAt = capturedAt.truncatedTo(ChronoUnit.MILLIS);
    }
}
