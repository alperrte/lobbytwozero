package com.ltz.auth.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.ltz.auth.application.port.AuthEventDeliveryException;
import com.ltz.auth.application.port.AuthEventEnvelope;
import com.ltz.auth.application.port.AuthEventSenderPort;
import com.ltz.auth.domain.entity.AuthEvent;
import com.ltz.auth.domain.enums.AuthEventType;
import com.ltz.auth.infrastructure.config.EventProperties;
import com.ltz.auth.infrastructure.persistence.repository.AuthEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Outbox aktarıcısı. Çalışan olayı kısa bir işlemde "kiralar", HTTP çağrısı işlem DIŞINDA yapılır; eşzamanlı işlemciler
 * aynı olayı almaz (SKIP LOCKED), çöken işlemci olayı kaybettirmez (kira dolunca yeniden denenir). Hata sonrası üstel
 * geri çekilme uygulanır; teslim edilmiş satırlar saklama süresi sonunda silinir. Alıcı {@code eventId} ile tekilleştirir,
 * bu yüzden en az bir kez teslim güvenlidir.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthEventRelay {

    static final int EVENT_VERSION = 1;
    static final String PRODUCER = "auth-service";

    private final AuthEventRepository repository;
    private final AuthEventSenderPort sender;
    private final EventProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /** @return bu turda teslim edilen olay sayısı */
    public int processDue() {
        transactionTemplate.executeWithoutResult(status ->
                repository.deleteDeliveredBefore(clock.instant().minus(properties.deliveredRetention())));
        List<ClaimedEvent> claimed = transactionTemplate.execute(status -> claimDue());
        int delivered = 0;
        for (ClaimedEvent event : claimed == null ? List.<ClaimedEvent>of() : claimed) {
            try {
                sender.send(event.target(), envelope(event));
            } catch (AuthEventDeliveryException exception) {
                recordFailure(event, exception.code());
                continue;
            }
            recordDelivered(event);
            delivered++;
        }
        return delivered;
    }

    private List<ClaimedEvent> claimDue() {
        Instant now = clock.instant();
        List<AuthEvent> due = repository.findDue(now, PageRequest.of(0, properties.batchSize()));
        due.forEach(event -> event.claim(now, properties.lease()));
        return due.stream().map(ClaimedEvent::from).toList();
    }

    private void recordDelivered(ClaimedEvent claimed) {
        transactionTemplate.executeWithoutResult(status -> repository.findById(claimed.id())
                .filter(AuthEvent::isPending)
                .ifPresent(event -> event.deliver(clock.instant())));
    }

    private void recordFailure(ClaimedEvent claimed, String errorCode) {
        transactionTemplate.executeWithoutResult(status -> repository.findById(claimed.id())
                .filter(AuthEvent::isPending)
                .ifPresent(event -> {
                    event.fail(clock.instant(), errorCode, properties.initialBackoff(), properties.maxBackoff());
                    if (event.getAttempts() == properties.stuckAttempts()) {
                        log.warn("Auth event delivery stuck target={} attempts={} error={}", event.getTarget(),
                                event.getAttempts(), errorCode);
                    }
                }));
    }

    private static AuthEventEnvelope envelope(ClaimedEvent event) {
        Map<String, Object> payload = switch (event.type()) {
            case ACCOUNT_STATUS_CHANGED -> Map.of("accountId", event.accountId().toString(), "status", event.value());
            case USER_REGISTERED -> Map.of("accountId", event.accountId().toString(), "username", event.value());
            case ACCOUNT_EMAIL_CHANGED -> Map.of("accountId", event.accountId().toString());
        };
        return new AuthEventEnvelope(event.eventId(), event.type().eventName(), EVENT_VERSION, event.occurredAt(),
                PRODUCER, event.accountId(), payload);
    }

    /** Kiralanan olayın işlem dışı kullanılacak anlık görüntüsü. */
    private record ClaimedEvent(UUID id, UUID eventId, UUID accountId, AuthEventType type, String value, String target,
                                Instant occurredAt) {

        static ClaimedEvent from(AuthEvent event) {
            return new ClaimedEvent(event.getId(), event.getEventId(), event.getAccountId(), event.getType(),
                    event.getValue(), event.getTarget(), event.getOccurredAt());
        }
    }
}
