package com.ltz.social.infrastructure.messaging.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ltz.social.application.service.OutboxRelay;

import lombok.RequiredArgsConstructor;

/** Relay'i periyodik çalıştırır. Yalnız {@code ltz.social.outbox.relay-enabled=true} iken yüklenir. */
@Component
@ConditionalOnProperty(name = "ltz.social.outbox.relay-enabled", havingValue = "true")
@RequiredArgsConstructor
public class OutboxRelayWorker {

    private final OutboxRelay relay;

    @Scheduled(fixedDelayString = "${ltz.social.outbox.poll-interval}")
    void run() {
        relay.deliverDue();
    }
}
