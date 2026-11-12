package com.ltz.messaging.application.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ltz.messaging.application.port.MessageStreamPort;

import lombok.RequiredArgsConstructor;

/** Silinen hesabın açık akışları, silme commit edildikten sonra (tüm örneklerde) kapatılır. */
@Component
@RequiredArgsConstructor
public class AccountErasureDispatcher {

    private final MessageStreamPort streams;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onErased(AccountErased event) {
        streams.closeStreams(event.accountId());
    }
}
