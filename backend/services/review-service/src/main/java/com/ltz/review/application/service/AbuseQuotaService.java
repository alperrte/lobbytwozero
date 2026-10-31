package com.ltz.review.application.service;

import com.ltz.review.domain.enums.AbuseAction;
import com.ltz.review.domain.exception.RateLimitExceededException;
import com.ltz.review.infrastructure.config.AbuseControlProperties;
import com.ltz.review.infrastructure.persistence.repository.AbuseQuotaRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Hesap başına sabit pencere kotası. Pencereler epoch saniyesine hizalanır; böylece tüm servis örnekleri
 * aynı pencereyi ve aynı PostgreSQL satırını kullanır. Her çağrı bir deneme sayılır (tekrar, geçersiz ve
 * reddedilen istekler dahil); aşımda Retry-After pencere bitimine göre hesaplanır.
 */
@Service
public class AbuseQuotaService {
    private final AbuseQuotaRepository quotas;
    private final AbuseControlProperties properties;
    private final Clock clock;

    public AbuseQuotaService(AbuseQuotaRepository quotas, AbuseControlProperties properties, Clock clock) {
        this.quotas = quotas;
        this.properties = properties;
        this.clock = clock;
    }

    /** Kotayı tüketir; aşıldıysa {@link RateLimitExceededException} fırlatır. Çağıranın transaction'ı gerekmez. */
    public void require(UUID account, AbuseAction action) {
        var rule = properties.rule(action);
        Instant now = clock.instant();
        long windowSeconds = rule.window().toSeconds();
        long startSecond = now.getEpochSecond() - Math.floorMod(now.getEpochSecond(), windowSeconds);
        Instant windowStart = Instant.ofEpochSecond(startSecond);
        Instant windowEnd = Instant.ofEpochSecond(startSecond + windowSeconds);
        int attempts = quotas.consume(account, action, windowStart, windowEnd, rule.limit() + 1);
        if (attempts > rule.limit()) throw new RateLimitExceededException(retryAfterSeconds(now, windowEnd));
    }

    static long retryAfterSeconds(Instant now, Instant windowEnd) {
        long remainingMillis = windowEnd.toEpochMilli() - now.toEpochMilli();
        return Math.max(1, (remainingMillis + 999) / 1000);
    }
}
