package com.ltz.social.application.port;

/**
 * Outbox'taki olayı tüketici servise iletir. Teslim edilemezse {@link EventDeliveryException} fırlatır; relay
 * olayı üstel geri çekilmeyle yeniden dener. Gerçek uygulama {@code CommunityEventPublisher}'dır
 * ({@code ltz.social.outbox.adapter=community}); adaptör seçilmedikçe {@code UnavailableEventPublisher} kullanılır.
 */
public interface SocialEventPublisher {

    void publish(EventEnvelope envelope);

    /**
     * Yayıncı şu an teslim denemeye hazır mı. Hazır değilse (ör. servis kimliği yapılandırılmamış) relay olayları
     * hiç almaz; böylece boşuna deneme hakkı yanmaz ve olay outbox'ta bekler.
     */
    default boolean isReady() {
        return true;
    }
}
