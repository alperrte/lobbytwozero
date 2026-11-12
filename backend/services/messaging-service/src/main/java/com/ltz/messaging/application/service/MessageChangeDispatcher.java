package com.ltz.messaging.application.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ltz.messaging.application.port.MessageStreamPort;

import lombok.RequiredArgsConstructor;

/** Mesaj değişikliği yalnız commit edildikten sonra akışlara iletilir: geri alınan değişiklik kimseye gitmez. */
@Component
@RequiredArgsConstructor
public class MessageChangeDispatcher {

    private final MessageStreamPort streams;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageChanged(MessageChanged event) {
        streams.publishEvent(event.eventName(), event.payloadByRecipient());
    }
}
