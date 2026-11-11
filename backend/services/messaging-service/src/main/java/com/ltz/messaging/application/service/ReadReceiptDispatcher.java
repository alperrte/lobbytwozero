package com.ltz.messaging.application.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ltz.messaging.application.port.MessageStreamPort;

import lombok.RequiredArgsConstructor;

/**
 * Okundu bilgisi yalnız commit edildikten sonra akışlara iletilir: geri alınan okuma kimseye gitmez. Olay kaçarsa
 * istemci geçmiş ucundaki {@code readStatus} ile eşitlenir.
 */
@Component
@RequiredArgsConstructor
public class ReadReceiptDispatcher {

    private final MessageStreamPort streams;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReadReceiptsCreated(ReadReceiptsCreated event) {
        event.byRecipient().forEach(streams::publishReadReceipt);
    }
}
