package com.ltz.auth.application.service;

import java.time.Clock;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.auth.domain.entity.SecurityEvent;
import com.ltz.auth.domain.enums.SecurityEventType;
import com.ltz.auth.infrastructure.persistence.repository.SecurityEventRepository;
import com.ltz.auth.infrastructure.security.RequestIdFilter;

import lombok.RequiredArgsConstructor;

/**
 * AUTH-15 olay kaydı. Açık işlem varsa ona katılır; böylece olay ve iş verisi birlikte geri alınır.
 * Yalnız olay türü, hesap ve istek kimliği yazılır; parola, kod, token veya kişisel veri yazılmaz.
 */
@Service
@RequiredArgsConstructor
public class SecurityEventRecorder {

    private final SecurityEventRepository securityEventRepository;
    private final Clock clock;

    @Transactional
    public void record(SecurityEventType type, UUID accountId) {
        securityEventRepository.save(SecurityEvent.of(type, accountId, MDC.get(RequestIdFilter.MDC_KEY),
                clock.instant()));
    }
}
