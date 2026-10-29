package com.ltz.auth.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ltz.auth.domain.entity.AuthEvent;
import com.ltz.auth.domain.enums.AuthEventType;
import com.ltz.auth.infrastructure.config.EventProperties;
import com.ltz.auth.infrastructure.persistence.repository.AuthEventRepository;

import lombok.RequiredArgsConstructor;

/**
 * Transactional outbox: olayı, iş değişikliğini yapan <b>aynı işlemde</b> her hedef için bir satır olarak yazar; ayrı bir
 * aktarıcı ({@link AuthEventRelay}) teslim eder. Hedef listesi boşsa (varsayılan) hiçbir şey yazılmaz. Aynı olayın
 * bütün hedef satırları ortak {@code eventId} taşır. Çağıran işlem geri alınırsa olay da yazılmamış olur.
 */
@Component
@RequiredArgsConstructor
public class AuthEventPublisher {

    private final AuthEventRepository repository;
    private final EventProperties properties;
    private final Clock clock;

    /** @param value durum veya kullanıcı adı; değer taşımayan olay türlerinde {@code null} */
    public void publish(AuthEventType type, UUID accountId, String value) {
        if (properties.targets().isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        UUID eventId = UUID.randomUUID();
        for (String target : properties.targets()) {
            repository.save(AuthEvent.pending(eventId, accountId, type, value, target, now));
        }
    }
}
