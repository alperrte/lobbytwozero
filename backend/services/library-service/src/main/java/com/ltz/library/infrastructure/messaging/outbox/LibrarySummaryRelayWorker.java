package com.ltz.library.infrastructure.messaging.outbox;

import com.ltz.library.application.service.LibrarySummaryRelay;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Özet relay'ini periyodik çalıştırır. Yalnız {@code ltz.library.outbox.relay-enabled=true} iken yüklenir. */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "ltz.library.outbox.relay-enabled", havingValue = "true")
public class LibrarySummaryRelayWorker {
    private final LibrarySummaryRelay relay;

    public LibrarySummaryRelayWorker(LibrarySummaryRelay relay) { this.relay = relay; }

    @Scheduled(fixedDelayString = "${ltz.library.outbox.poll-interval:PT5S}", initialDelayString = "${ltz.library.outbox.initial-delay:PT20S}")
    void run() { relay.deliverDue(); }
}
