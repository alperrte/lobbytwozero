package com.ltz.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ltz.auth.domain.entity.AuthEvent;
import com.ltz.auth.domain.enums.AuthEventType;
import com.ltz.auth.infrastructure.config.EventProperties;
import com.ltz.auth.infrastructure.persistence.repository.AuthEventRepository;

class AuthEventPublisherTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-29T10:00:00Z"), ZoneOffset.UTC);

    private final AuthEventRepository repository = mock(AuthEventRepository.class);

    private AuthEventPublisher publisher(Set<String> targets) {
        EventProperties properties = new EventProperties(targets, false, Duration.ofSeconds(5), 20,
                Duration.ofMinutes(2), Duration.ofSeconds(10), Duration.ofHours(1), 20, Duration.ofDays(7),
                Duration.ofSeconds(5));
        return new AuthEventPublisher(repository, properties, CLOCK);
    }

    @Test
    void hedefYoksaHicSatirYazilmaz() {
        publisher(Set.of()).publish(AuthEventType.ACCOUNT_STATUS_CHANGED, UUID.randomUUID(), "BANNED");

        verify(repository, never()).save(any());
    }

    @Test
    void herHedefIcinOrtakEventIdliSatirYazilir() {
        UUID account = UUID.randomUUID();

        publisher(Set.of("community", "user")).publish(AuthEventType.USER_REGISTERED, account, "oyuncu_1");

        ArgumentCaptor<AuthEvent> saved = ArgumentCaptor.forClass(AuthEvent.class);
        verify(repository, times(2)).save(saved.capture());
        List<AuthEvent> events = saved.getAllValues();
        assertThat(events).extracting(AuthEvent::getTarget).containsExactlyInAnyOrder("community", "user");
        assertThat(events).extracting(AuthEvent::getEventId).containsOnly(events.getFirst().getEventId());
        assertThat(events).extracting(AuthEvent::getAccountId).containsOnly(account);
        assertThat(events).extracting(AuthEvent::getValue).containsOnly("oyuncu_1");
    }
}
