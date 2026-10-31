package com.ltz.review.application.service;

import com.ltz.review.domain.enums.AbuseAction;
import com.ltz.review.domain.exception.RateLimitExceededException;
import com.ltz.review.infrastructure.config.AbuseControlProperties;
import com.ltz.review.infrastructure.persistence.repository.AbuseQuotaRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbuseQuotaServiceTest {
    final AbuseQuotaRepository repository = mock(AbuseQuotaRepository.class);
    final UUID account = UUID.randomUUID();

    AbuseQuotaService service(Instant now, AbuseControlProperties properties) {
        return new AbuseQuotaService(repository, properties, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test void windowIsAlignedToEpochAndCounterIsCappedAtLimitPlusOne() {
        Instant now = Instant.parse("2026-11-01T12:00:37.250Z");
        when(repository.consume(eq(account), eq(AbuseAction.REVIEW_CREATE), eq(Instant.parse("2026-11-01T12:00:00Z")),
                eq(Instant.parse("2026-11-01T12:01:00Z")), eq(6))).thenReturn(5);
        assertDoesNotThrow(() -> service(now, new AbuseControlProperties(null, null)).require(account, AbuseAction.REVIEW_CREATE));
        verify(repository).consume(eq(account), eq(AbuseAction.REVIEW_CREATE), eq(Instant.parse("2026-11-01T12:00:00Z")),
                eq(Instant.parse("2026-11-01T12:01:00Z")), eq(6));
    }

    @Test void exceededQuotaReportsRoundedUpRetryAfter() {
        Instant now = Instant.parse("2026-11-01T12:00:37.250Z");
        when(repository.consume(eq(account), eq(AbuseAction.REVIEW_LIKE), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyInt()))
                .thenReturn(61);
        var thrown = assertThrows(RateLimitExceededException.class,
                () -> service(now, new AbuseControlProperties(null, null)).require(account, AbuseAction.REVIEW_LIKE));
        assertEquals(23, thrown.retryAfterSeconds()); // 22,75 sn kaldı
        assertEquals(429, thrown.status());
    }

    @Test void customWindowsAreAlignedIndependentlyPerAction() {
        var properties = new AbuseControlProperties(new AbuseControlProperties.Rule(2, Duration.ofSeconds(10)), null);
        when(repository.consume(eq(account), eq(AbuseAction.REVIEW_CREATE), eq(Instant.parse("2026-11-01T12:00:30Z")),
                eq(Instant.parse("2026-11-01T12:00:40Z")), eq(3))).thenReturn(3);
        var thrown = assertThrows(RateLimitExceededException.class,
                () -> service(Instant.parse("2026-11-01T12:00:39.999Z"), properties).require(account, AbuseAction.REVIEW_CREATE));
        assertEquals(1, thrown.retryAfterSeconds());
    }

    @Test void retryAfterIsAlwaysAtLeastOneSecond() {
        Instant end = Instant.parse("2026-11-01T12:01:00Z");
        assertEquals(1, AbuseQuotaService.retryAfterSeconds(end, end));
        assertEquals(1, AbuseQuotaService.retryAfterSeconds(end.plusMillis(5), end));
        assertEquals(60, AbuseQuotaService.retryAfterSeconds(end.minusSeconds(60), end));
    }
}
