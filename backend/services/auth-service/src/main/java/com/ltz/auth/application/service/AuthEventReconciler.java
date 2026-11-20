package com.ltz.auth.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.auth.domain.entity.Account;
import com.ltz.auth.domain.enums.AuthEventType;
import com.ltz.auth.infrastructure.config.EventProperties;
import com.ltz.auth.infrastructure.config.EventReconcileProperties;
import com.ltz.auth.infrastructure.persistence.repository.AccountRepository;

import lombok.RequiredArgsConstructor;

/**
 * Hesap durumu olaylarının onarımı. Olay yazımı hedefe bağlıdır ({@code AUTH_EVENT_TARGETS} boşsa ya da hedef sonradan
 * eklenmişse eski değişiklikler hiç yazılmaz); bu hizmet o boşluğu kapatır. Her hedef için, durumu son
 * {@code window} içinde değişmiş ve o duruma ait satırı olmayan hesapların <b>güncel</b> durumunu, gerçek değişim
 * zamanıyla yeniden yazar. Alıcı olayı {@code occurredAt} ile sıralar, bu yüzden yeniden gönderim daha yeni bir durumu
 * ezemez; yinelenen teslim de zararsızdır (alıcı tekilleştirir).
 */
@Service
@EnableConfigurationProperties(EventReconcileProperties.class)
@RequiredArgsConstructor
public class AuthEventReconciler {

    private final AccountRepository accountRepository;
    private final AuthEventPublisher eventPublisher;
    private final EventProperties events;
    private final EventReconcileProperties properties;
    private final Clock clock;

    /** @return bu turda yeniden yazılan olay sayısı (hedef başına satır) */
    @Transactional
    public int reconcile() {
        Instant now = clock.instant();
        Instant from = now.minus(properties.window());
        Instant to = now.minus(properties.grace());
        int republished = 0;
        for (String target : events.targets()) {
            List<Account> gaps = accountRepository.findStatusEventGapsForUpdate(target, from, to,
                    PageRequest.of(0, properties.batchSize()));
            for (Account account : gaps) {
                eventPublisher.republishTo(target, AuthEventType.ACCOUNT_STATUS_CHANGED, account.getId(),
                        account.getStatus().name(), account.getStatusChangedAt());
                republished++;
            }
        }
        return republished;
    }
}
