package com.ltz.messaging.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.messaging.application.port.AccountDataEraserPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Auth'un hesap silme çağrısı (D4). Hesabın kişisel verisi tek transaction'da silinir ya da anonimleşir
 * ({@link AccountDataEraserPort}); yarım kalan silme olmaz, ikinci çağrı aynı sonucu verir. Commit sonrası hesabın tüm
 * örneklerdeki açık akışları kapatılır. Günlüğe yalnız hesap kimliği yazılır.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountErasureService {

    private final AccountDataEraserPort eraser;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public void erase(UUID accountId) {
        eraser.erase(accountId, Instant.now(clock).truncatedTo(ChronoUnit.MICROS));
        events.publishEvent(new AccountErased(accountId));
        log.info("Messaging data erased for account {}", accountId);
    }
}
