package com.ltz.game.application.service;

import com.ltz.game.domain.exception.ProviderException;
import java.time.Duration;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Beş otomatik tekrar; süreç yeniden başlasa da deneme sayısı DB'de korunur. */
@Component
public class ProviderRetryPolicy {
    private static final Set<String> TRANSIENT = Set.of("RATE_LIMITED", "PROVIDER_UNAVAILABLE", "PROVIDER_TRANSPORT_ERROR");
    public Duration delay(ProviderException error, int failures) {
        if (failures < 1 || failures > 5 || !TRANSIENT.contains(error.code())) return null;
        Duration delay = Duration.ofSeconds(Math.min(300L, 5L << (failures - 1)));
        if (error.code().equals("RATE_LIMITED") && delay.compareTo(Duration.ofSeconds(60)) < 0)
            delay = Duration.ofSeconds(60);
        Duration requested = error.retryAfter();
        if (requested != null && requested.compareTo(delay) > 0) delay = requested;
        // Üst sınırı aşan Retry-After erken çağrıya çevrilmez; operatör müdahalesi beklenir.
        return delay.compareTo(Duration.ofDays(1)) > 0 ? null : delay;
    }
}
