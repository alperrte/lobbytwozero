package com.ltz.performance.domain.entity;

import com.ltz.performance.domain.enums.SessionStatus;
import com.ltz.performance.domain.exception.PerformanceConflictException;
import com.ltz.performance.domain.exception.PerformanceUnprocessableException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;

/**
 * Örnek gönderimi kuralları. Sıra önemli değildir: sırasız gelen (arabelleğe alınmış) örnekler kabul edilir,
 * sıralama okuma sırasında zamana göre yapılır. Kural ihlali tüm gönderimi reddeder (kısmi kabul yoktur); hata,
 * değeri yansıtmadan yalnız ihlal eden örneğin sırasını bildirir.
 */
public final class SampleBatchRules {
    /** Cihaz ve sunucu saatleri arasındaki izin verilen fark; oturum sınırlarında ve "gelecek" denetiminde kullanılır. */
    public static final Duration CLOCK_SKEW = Duration.ofMinutes(5);
    /** Oturum bittikten sonra arabellekteki örneklerin gönderilebileceği süre. */
    public static final Duration END_GRACE = Duration.ofMinutes(10);

    private SampleBatchRules() { }

    /** ACTIVE (24 saatten eski değil) veya yakın zamanda COMPLETED oturum örnek kabul eder. */
    public static void requireAccepting(SessionStatus status, Instant startedAt, Instant endedAt, Instant now) {
        boolean open = status == SessionStatus.ACTIVE && startedAt.plus(PerformanceSession.MAX_ACTIVE).isAfter(now);
        boolean justCompleted = status == SessionStatus.COMPLETED && endedAt != null
                && endedAt.plus(END_GRACE).isAfter(now);
        if (!open && !justCompleted) {
            throw new PerformanceConflictException(PerformanceConflictException.SESSION_NOT_ACCEPTING_SAMPLES);
        }
    }

    public static void validate(List<TelemetrySample> samples, Instant startedAt, Instant endedAt, Instant now) {
        Instant lower = startedAt.minus(CLOCK_SKEW);
        Instant upper = now.plus(CLOCK_SKEW);
        if (endedAt != null && endedAt.plus(CLOCK_SKEW).isBefore(upper)) upper = endedAt.plus(CLOCK_SKEW);
        var seen = new HashSet<String>();
        for (int index = 0; index < samples.size(); index++) {
            var sample = samples.get(index);
            if (!sample.metric().accepts(sample.value())) {
                throw new PerformanceUnprocessableException(PerformanceUnprocessableException.SAMPLE_VALUE_INVALID, index);
            }
            if (sample.capturedAt().isBefore(lower) || sample.capturedAt().isAfter(upper)) {
                throw new PerformanceUnprocessableException(PerformanceUnprocessableException.SAMPLE_TIME_INVALID, index);
            }
            if (!seen.add(sample.metric() + "@" + sample.capturedAt().toEpochMilli())) {
                throw new PerformanceUnprocessableException(
                        PerformanceUnprocessableException.SAMPLE_DUPLICATE_IN_BATCH, index);
            }
        }
    }
}
