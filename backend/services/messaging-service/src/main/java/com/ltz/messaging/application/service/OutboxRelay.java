package com.ltz.messaging.application.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ltz.messaging.application.port.EventDeliveryException;
import com.ltz.messaging.application.port.EventEnvelope;
import com.ltz.messaging.application.port.EventPublisherPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Outbox'taki zamanı gelmiş olayları tüketiciye iletir (en az bir kez; tüketici {@code eventId} ile tekilleştirir). Her
 * olayın sonucu ayrı kaydedilir: bir olayın başarısızlığı diğerlerini engellemez. Mesaj kaydı teslimden bağımsızdır
 * (MSG-27): teslim hatası mesajı etkilemez. Loglara yalnız olay türü ve kimliği yazılır, yük yazılmaz.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxEventStore store;
    private final EventPublisherPort publisher;

    /** Bir tur çalışır; başarıyla teslim edilen olay sayısını döner. */
    public int deliverDue() {
        if (!publisher.isReady()) {
            return 0;
        }
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
