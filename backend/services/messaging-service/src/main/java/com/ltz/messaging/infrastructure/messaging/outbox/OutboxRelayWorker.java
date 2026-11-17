package com.ltz.messaging.infrastructure.messaging.outbox;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ltz.messaging.application.service.OutboxEventStore;
import com.ltz.messaging.application.service.OutboxRelay;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Relay'i ve saklama temizliğini periyodik çalıştırır. Teslim hedefi yokken ({@code adapter=unavailable}) relay hiçbir
 * olayı almaz; temizlik her durumda çalışır, tablo sınırsız büyümez. Yalnız sayı loglanır.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayWorker {

    private final OutboxRelay relay;
    private final OutboxEventStore store;

    @Scheduled(initialDelayString = "${ltz.messaging.outbox.poll-interval}",
            fixedDelayString = "${ltz.messaging.outbox.poll-interval}")
    void deliver() {
        relay.deliverDue();
    }

    @Scheduled(initialDelayString = "${ltz.messaging.outbox.cleanup-interval}",
            fixedDelayString = "${ltz.messaging.outbox.cleanup-interval}")
    void cleanup() {
        int deleted = store.deleteExpired();
        if (deleted > 0) {
            log.info("Deleted {} outbox events past retention", deleted);
        }
    }
}
