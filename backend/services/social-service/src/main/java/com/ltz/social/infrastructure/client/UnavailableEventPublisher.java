package com.ltz.social.infrastructure.client;

import com.ltz.social.application.port.EventDeliveryException;
import com.ltz.social.application.port.EventEnvelope;
import com.ltz.social.application.port.SocialEventPublisher;

/**
 * Varsayılan uygulama: gerçek teslim yok. notification-service henüz iç uç sunmuyor ve servisler arası kimlik
 * (D4) hazır değil; kimliksiz bir iç uca olay göndermek yasak olduğundan her teslim reddedilir ve olay outbox'ta
 * bekler. Relay zaten varsayılan olarak kapalıdır; gerçek adaptör eklenince bu bean otomatik devre dışı kalır.
 */
public class UnavailableEventPublisher implements SocialEventPublisher {

    @Override
    public void publish(EventEnvelope envelope) {
        throw new EventDeliveryException("No event delivery adapter is available");
    }
}
