package com.ltz.messaging.application.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ltz.messaging.application.port.MessageStreamPort;

import lombok.RequiredArgsConstructor;

/**
 * Mesaj yalnız commit edildikten sonra akışlara iletilir: geri alınan mesaj kimseye gitmez, iletilen her mesaj geçmiş
 * ucunda da bulunur.
 */
@Component
@RequiredArgsConstructor
public class MessageStreamDispatcher {

    private final MessageStreamPort streams;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageCreated(MessageCreated event) {
        streams.publish(event.recipients(), event.message());
    }
}
