package com.ltz.mail.application.service;

import java.time.Duration;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ltz.mail.infrastructure.config.WorkerProperties;

/**
 * Geçici hatalarda üstel geri çekilme (MAIL-12): {@code taban × 2^(deneme-1)}, üst sınırı aşmaz. Aynı anda başarısız
 * olan kayıtlar aynı saniyede toplanmasın diye ±%20 sabit bir sapma uygulanır; sapma kayıt kimliğinden türetildiği için
 * rastgele sayı üreticisi gerekmez ve test edilebilirdir.
 */
@Component
public class MailRetryPolicy {

    private static final int MAX_SHIFT = 20;
    private static final double JITTER_RATIO = 0.2;
    private static final double BYTE_RANGE = 255.0;

    private final WorkerProperties worker;

    public MailRetryPolicy(WorkerProperties worker) {
        this.worker = worker;
    }

    /** Deneme sayısı üst sınıra ulaştıysa yeniden denenmez. */
    public boolean isExhausted(int attempts) {
        return attempts >= worker.maxAttempts();
    }

    /** @param attempts şimdiye kadar yapılan deneme sayısı (≥ 1) */
    public Duration delayAfter(int attempts, UUID messageId) {
        int shift = Math.min(Math.max(attempts - 1, 0), MAX_SHIFT);
        Duration exponential = worker.baseDelay().multipliedBy(1L << shift);
        Duration capped = exponential.compareTo(worker.maxDelay()) > 0 ? worker.maxDelay() : exponential;
        double unit = (messageId.hashCode() & 0xFF) / BYTE_RANGE * 2 - 1;
        long millis = Math.round(capped.toMillis() * (1 + JITTER_RATIO * unit));
        return Duration.ofMillis(Math.max(millis, 1));
    }
}
