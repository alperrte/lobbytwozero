package com.ltz.messaging.application.service;

import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.messaging.domain.entity.OutboxEvent;
import com.ltz.messaging.domain.event.MessageSentEvent;
import com.ltz.messaging.infrastructure.persistence.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * Olayı outbox'a yazar. {@code MANDATORY}: yalnız iş verisini yazan transaction içinde çağrılabilir; mesaj ve olay birlikte
 * kalıcı olur ya da birlikte geri alınır. Mesaj kaydı bildirim tesliminden bağımsızdır (MSG-27).
 */
@Component
@RequiredArgsConstructor
public class OutboxRecorder {

    private final OutboxEventRepository outbox;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void messageSent(MessageSentEvent event, Instant now) {
        outbox.save(OutboxEvent.pending(MessageSentEvent.AGGREGATE_TYPE, event.conversationId(),
                MessageSentEvent.EVENT_TYPE, MessageSentEvent.EVENT_VERSION, objectMapper.writeValueAsString(event),
                now));
    }
}
