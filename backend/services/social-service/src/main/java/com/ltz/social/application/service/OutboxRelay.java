package com.ltz.social.application.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ltz.social.application.port.EventDeliveryException;
import com.ltz.social.application.port.EventEnvelope;
import com.ltz.social.application.port.SocialEventPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Outbox'taki zamanı gelmiş olayları tüketiciye iletir (en az bir kez). Her olay için sonuç ayrı kaydedilir:
 * bir olayın başarısızlığı diğerlerini engellemez. Loglara yalnız olay türü ve kimliği yazılır, yük yazılmaz.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxEventStore store;
    private final SocialEventPublisher publisher;

    /** Bir tur çalışır; başarıyla teslim edilen olay sayısını döner. */
    public int deliverDue() {
        List<EventEnvelope> claimed = store.claimDue();
        int delivered = 0;
        for (EventEnvelope envelope : claimed) {
            try {
                publisher.publish(envelope);
                store.markSent(envelope.eventId());
                delivered++;
            } catch (EventDeliveryException exception) {
                log.warn("Event delivery failed: type={}, id={}, reason={}", envelope.eventType(),
                        envelope.eventId(), exception.getMessage());
                store.markFailed(envelope.eventId());
            }
        }
        return delivered;
    }
}
