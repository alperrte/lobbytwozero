package com.ltz.messaging.application.port;

/**
 * Outbox'taki olayı tüketici servise iletir (MSG-16). Teslim edilemezse {@link EventDeliveryException} fırlatır; relay
 * olayı üstel geri çekilmeyle yeniden dener.
 */
public interface EventPublisherPort {

    void publish(EventEnvelope envelope);

    /**
     * Yayıncı şu an teslim denemeye hazır mı. Hazır değilse (hedef seçilmemiş ya da servis kimliği yok) relay olayları
     * hiç almaz; deneme hakkı yanmaz ve olay outbox'ta bekler.
     */
    boolean isReady();
}
