package com.ltz.user.application.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ltz.user.domain.enums.AccountStatus;
import com.ltz.user.domain.enums.LinkedProvider;
import com.ltz.user.infrastructure.persistence.repository.LinkedAccountRepository;
import com.ltz.user.infrastructure.persistence.repository.UserProfileRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Auth'un dış hesap olayları (USER-07/08): {@code ExternalIdentityLinked} / {@code ExternalIdentityUnlinked}, yük
 * {@code {accountId, provider, providerSubject}} ({@code accountId = aggregateId}). Kimlik kanıtı yalnız Auth'ta üretilir;
 * User bağlantıyı bu olaylardan yansıtır. Her yazma son-durum upsert'üdür ve olayın {@code occurredAt} zamanıyla korunur.
 * Tanımadığı sağlayıcı sessizce yok sayılır (Auth yeni sağlayıcı eklerse User kırılmaz). Çağıran olayı aynı transaction'da
 * inbox'a yazar.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class LinkedAccountEventHandler {

    private final LinkedAccountRepository accounts;
    private final UserProfileRepository profiles;

    void linked(IncomingEvent event, Instant now) {
        UUID accountId = accountId(event);
        Optional<LinkedProvider> provider = provider(event);
        if (provider.isEmpty()) {
            return;
        }
        String subject = EventPayload.text(event.payload(), "providerSubject");
        if (!provider.get().isValidSubject(subject)
                || accounts.existsByProviderAndExternalIdAndUserIdNot(provider.get(), subject, accountId)) {
            throw EventPayload.invalid();
        }
        profiles.insertIfAbsent(accountId, AccountStatus.ACTIVE.name(), null, now);
        accounts.upsertLinked(UUID.randomUUID(), accountId, provider.get().name(), subject, event.occurredAt());
    }

    void unlinked(IncomingEvent event, Instant now) {
        UUID accountId = accountId(event);
        Optional<LinkedProvider> provider = provider(event);
        if (provider.isEmpty()) {
            return;
        }
        profiles.insertIfAbsent(accountId, AccountStatus.ACTIVE.name(), null, now);
        accounts.upsertUnlinked(UUID.randomUUID(), accountId, provider.get().name(), event.occurredAt());
    }

    private static UUID accountId(IncomingEvent event) {
        UUID accountId = EventPayload.uuid(event.payload(), "accountId");
        if (!accountId.equals(event.aggregateId())) {
            throw EventPayload.invalid();
        }
        return accountId;
    }

    private static Optional<LinkedProvider> provider(IncomingEvent event) {
        Optional<LinkedProvider> provider = LinkedProvider.parse(EventPayload.text(event.payload(), "provider"));
        if (provider.isEmpty()) {
            log.info("Event for an unsupported provider ignored: eventType={}", event.eventType());
        }
        return provider;
    }
}
