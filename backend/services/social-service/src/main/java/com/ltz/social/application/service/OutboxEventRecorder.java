package com.ltz.social.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.social.domain.entity.OutboxEvent;
import com.ltz.social.domain.event.SocialEvent;
import com.ltz.social.infrastructure.persistence.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * Olayı outbox'a yazar. {@code MANDATORY}: yalnız iş verisini değiştiren bir transaction içinde çağrılabilir; böylece
 * olay ile iş verisi birlikte kalıcı olur ya da birlikte geri alınır (olay kaybı ve hayalet olay olmaz).
 */
@Component
@RequiredArgsConstructor
public class OutboxEventRecorder {

    private final OutboxEventRepository outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(SocialEvent event) {
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        outbox.save(OutboxEvent.pending(event, objectMapper.writeValueAsString(event), now));
    }
}
