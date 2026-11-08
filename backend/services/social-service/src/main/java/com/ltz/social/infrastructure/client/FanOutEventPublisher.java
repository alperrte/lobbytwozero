package com.ltz.social.infrastructure.client;

import java.util.List;

import com.ltz.social.application.port.EventDeliveryException;
import com.ltz.social.application.port.EventEnvelope;
import com.ltz.social.application.port.SocialEventPublisher;

/**
 * Bir olayı türünü işleyen her hedefe sırayla iletir ({@code ltz.social.outbox.adapter=services}): engel olayları
 * community ve user'a, arkadaşlık değişikliği yalnız user'a gider. Hedeflerden biri bile alamazsa olay teslim edilmemiş
 * sayılır ve relay tamamını yeniden dener; alıcılar {@code eventId} ile tekilleştirdiğinden (community inbox, user
 * {@code DUPLICATE}) önceden alan hedef olayı ikinci kez uygulamaz. Hiçbir hedefin işlemediği tür (ör. bildirim
 * olayları) {@link EventDeliveryException} ile outbox'ta bekler.
 */
public class FanOutEventPublisher implements SocialEventPublisher {

    private final List<ServiceEventPublisher> targets;

    public FanOutEventPublisher(List<ServiceEventPublisher> targets) {
        this.targets = List.copyOf(targets);
    }

    /** Hedefler aynı servis kimliğini kullanır; biri hazır değilse olay hiç denenmez. */
    @Override
    public boolean isReady() {
        return targets.stream().allMatch(ServiceEventPublisher::isReady);
    }

    @Override
    public void publish(EventEnvelope envelope) {
        List<ServiceEventPublisher> matching = targets.stream()
                .filter(target -> target.handles(envelope.eventType()))
                .toList();
        if (matching.isEmpty()) {
            throw new EventDeliveryException("No delivery target for this event type");
        }
        matching.forEach(target -> target.publish(envelope));
    }
}
