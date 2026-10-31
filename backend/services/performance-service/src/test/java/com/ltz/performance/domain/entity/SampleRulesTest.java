package com.ltz.performance.domain.entity;

import com.ltz.performance.api.dto.request.SyncBatchRequest;
import com.ltz.performance.domain.enums.SampleMetric;
import com.ltz.performance.domain.enums.SessionStatus;
import com.ltz.performance.domain.exception.PerformanceConflictException;
import com.ltz.performance.domain.exception.PerformanceUnprocessableException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SampleRulesTest {
    private static final Instant START = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant NOW = START.plusSeconds(600);

    private static TelemetrySample sample(Instant at, SampleMetric metric, double value) {
        return new TelemetrySample(at, metric, value);
    }

    @Test
    void metricsParseOnlyCanonicalNamesAndHaveFixedUnits() {
        for (SampleMetric metric : SampleMetric.values()) {
            assertThat(SampleMetric.parse(metric.name())).isSameAs(metric);
            assertThat(metric.unit()).isNotBlank();
        }
        assertThat(SampleMetric.parse("fps")).isNull();
        assertThat(SampleMetric.parse(" FPS")).isNull();
        assertThat(SampleMetric.parse(null)).isNull();
        assertThat(SampleMetric.FPS.unit()).isEqualTo("FPS");
        assertThat(SampleMetric.FAN_RPM.unit()).isEqualTo("RPM");
    }

    @Test
    void valueBoundsAreMetricSpecific() {
        assertThat(SampleMetric.CPU_USAGE_PERCENT.accepts(0)).isTrue();
        assertThat(SampleMetric.CPU_USAGE_PERCENT.accepts(100)).isTrue();
        assertThat(SampleMetric.CPU_USAGE_PERCENT.accepts(100.0001)).isFalse();
        assertThat(SampleMetric.GPU_TEMP_C.accepts(0)).isFalse();
        assertThat(SampleMetric.GPU_TEMP_C.accepts(Double.MIN_VALUE)).isTrue();
        assertThat(SampleMetric.FPS.accepts(0)).isFalse();
        assertThat(SampleMetric.FPS.accepts(-1)).isFalse();
        assertThat(SampleMetric.FAN_RPM.accepts(0)).isTrue();
        for (SampleMetric metric : SampleMetric.values()) {
            assertThat(metric.accepts(Double.NaN)).as(metric.name()).isFalse();
            assertThat(metric.accepts(Double.POSITIVE_INFINITY)).as(metric.name()).isFalse();
            assertThat(metric.accepts(Double.NEGATIVE_INFINITY)).as(metric.name()).isFalse();
            assertThat(metric.accepts(-0.5)).as(metric.name()).isFalse();
        }
    }

    @Test
    void sampleTimestampIsTruncatedToMillis() {
        var at = Instant.parse("2026-01-01T10:00:00.123456789Z");
        assertThat(sample(at, SampleMetric.FPS, 60).capturedAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00.123Z"));
    }

    @Test
    void sessionAcceptsSamplesWhileActiveOrJustCompleted() {
        assertThatCode(SessionStatus.ACTIVE, START, null, NOW).doesNotThrowAnyException();
        var stale = START.plus(PerformanceSession.MAX_ACTIVE).plusSeconds(1);
        assertThatThrownBy(() -> SampleBatchRules.requireAccepting(SessionStatus.ACTIVE, START, null, stale))
                .isInstanceOf(PerformanceConflictException.class);
        Instant ended = START.plusSeconds(60);
        assertThatCode(SessionStatus.COMPLETED, START, ended, ended.plus(SampleBatchRules.END_GRACE).minusSeconds(1))
                .doesNotThrowAnyException();
        var late = ended.plus(SampleBatchRules.END_GRACE);
        assertThatThrownBy(() -> SampleBatchRules.requireAccepting(SessionStatus.COMPLETED, START, ended, late))
                .isInstanceOf(PerformanceConflictException.class);
        assertThatThrownBy(() -> SampleBatchRules.requireAccepting(SessionStatus.ABANDONED, START, ended, NOW))
                .isInstanceOf(PerformanceConflictException.class);
        assertThatThrownBy(() -> SampleBatchRules.requireAccepting(SessionStatus.COMPLETED, START, null, NOW))
                .isInstanceOf(PerformanceConflictException.class);
    }

    private static org.assertj.core.api.AbstractThrowableAssert<?, ? extends Throwable> assertThatCode(
            SessionStatus status, Instant started, Instant ended, Instant now) {
        return org.assertj.core.api.Assertions.assertThatCode(
                () -> SampleBatchRules.requireAccepting(status, started, ended, now));
    }

    @Test
    void validationReportsTheFirstOffendingIndexWithoutEchoingValues() {
        var ok = sample(START.plusSeconds(1), SampleMetric.FPS, 60);
        var badValue = sample(START.plusSeconds(2), SampleMetric.CPU_TEMP_C, 0);
        var badTime = sample(NOW.plusSeconds(301), SampleMetric.FPS, 60);
        var duplicate = sample(START.plusSeconds(1), SampleMetric.FPS, 61);

        List<TelemetrySample> value = List.of(ok, badValue);
        assertThatThrownBy(() -> SampleBatchRules.validate(value, START, null, NOW))
                .isInstanceOfSatisfying(PerformanceUnprocessableException.class, e -> {
                    assertThat(e.code()).isEqualTo(PerformanceUnprocessableException.SAMPLE_VALUE_INVALID);
                    assertThat(e.sampleIndex()).isEqualTo(1);
                });
        List<TelemetrySample> time = List.of(ok, badTime);
        assertThatThrownBy(() -> SampleBatchRules.validate(time, START, null, NOW))
                .isInstanceOfSatisfying(PerformanceUnprocessableException.class, e -> {
                    assertThat(e.code()).isEqualTo(PerformanceUnprocessableException.SAMPLE_TIME_INVALID);
                    assertThat(e.sampleIndex()).isEqualTo(1);
                });
        List<TelemetrySample> repeated = List.of(ok, duplicate);
        assertThatThrownBy(() -> SampleBatchRules.validate(repeated, START, null, NOW))
                .isInstanceOfSatisfying(PerformanceUnprocessableException.class, e -> {
                    assertThat(e.code()).isEqualTo(PerformanceUnprocessableException.SAMPLE_DUPLICATE_IN_BATCH);
                    assertThat(e.sampleIndex()).isEqualTo(1);
                });
    }

    @Test
    void completedSessionCapsTheUpperTimeBoundAtEndPlusSkew() {
        Instant ended = START.plusSeconds(60);
        Instant now = ended.plusSeconds(120);
        var inside = sample(ended.plus(SampleBatchRules.CLOCK_SKEW), SampleMetric.FPS, 60);
        var outside = sample(ended.plus(SampleBatchRules.CLOCK_SKEW).plusMillis(1), SampleMetric.FPS, 60);
        SampleBatchRules.validate(List.of(inside), START, ended, now);
        List<TelemetrySample> beyond = List.of(outside);
        assertThatThrownBy(() -> SampleBatchRules.validate(beyond, START, ended, now))
                .isInstanceOf(PerformanceUnprocessableException.class);
        // Sırasız ve sınır içi örnekler kabul edilir; başlangıçtan 5 dakika öncesi dahil.
        SampleBatchRules.validate(List.of(sample(START.plusSeconds(30), SampleMetric.FPS, 60),
                sample(START.minus(SampleBatchRules.CLOCK_SKEW), SampleMetric.FPS, 59)), START, ended, now);
    }

    @Test
    void requestShapeMatchesSchemaVersion() {
        UUID session = UUID.randomUUID();
        var item = new com.ltz.performance.api.dto.request.SampleItemRequest(START, "FPS", 60.0);
        new SyncBatchRequest(1, START, 3, null, null).requireShape();
        new SyncBatchRequest(2, START, null, session, List.of(item)).requireShape();
        new SyncBatchRequest(2, START, null, session, List.of()).requireShape();
        List<SyncBatchRequest> invalid = List.of(
                new SyncBatchRequest(1, START, null, null, null),
                new SyncBatchRequest(1, START, 1, session, null),
                new SyncBatchRequest(1, START, 1, null, List.of(item)),
                new SyncBatchRequest(2, START, null, null, List.of(item)),
                new SyncBatchRequest(2, START, null, session, null),
                new SyncBatchRequest(2, START, 1, session, List.of(item)));
        for (SyncBatchRequest request : invalid) {
            assertThatThrownBy(request::requireShape).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(new SyncBatchRequest(1, START, 3, null, null).effectiveSampleCount()).isEqualTo(3);
        assertThat(new SyncBatchRequest(2, START, null, session, List.of(item, item)).effectiveSampleCount())
                .isEqualTo(2);
    }
}
