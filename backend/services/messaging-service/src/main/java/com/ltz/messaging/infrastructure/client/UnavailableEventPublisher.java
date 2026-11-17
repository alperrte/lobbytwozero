package com.ltz.messaging.infrastructure.client;

import com.ltz.messaging.application.port.EventDeliveryException;
import com.ltz.messaging.application.port.EventEnvelope;
import com.ltz.messaging.application.port.EventPublisherPort;

/**
 * Teslim hedefi yok (varsayılan): notification-service alıcı ucu ve Auth'ta messaging → notification çifti hazır
 * olana kadar olaylar outbox'ta bekler. Hiçbir zaman hazır değildir; relay olay almaz, deneme hakkı yanmaz.
 */
public class UnavailableEventPublisher implements EventPublisherPort {

    @Override
    public boolean isReady() {
        return false;
    }

    @Override
    public void publish(EventEnvelope envelope) {
        throw new EventDeliveryException("No event delivery target is configured");
    }
}
