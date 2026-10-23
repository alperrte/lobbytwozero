package com.ltz.social.application.port;

/**
 * Outbox'taki olayı tüketici servise iletir. Teslim edilemezse {@link EventDeliveryException} fırlatır; relay
 * olayı üstel geri çekilmeyle yeniden dener. Gerçek bir uygulama, tüketici servisin servis kimlikli iç ucu
 * (D4) hazır olunca eklenir; o zamana kadar {@code UnavailableEventPublisher} kullanılır.
 */
public interface SocialEventPublisher {

    void publish(EventEnvelope envelope);
}
