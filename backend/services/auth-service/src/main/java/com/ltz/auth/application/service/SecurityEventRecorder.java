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
import lombok.extern.slf4j.Slf4j;

/**
 * AUTH-15 olay kaydı. Açık işlem varsa ona katılır; böylece olay ve iş verisi birlikte geri alınır.
 * Yalnız olay türü, hesap ve istek kimliği yazılır; parola, kod, token veya kişisel veri yazılmaz.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityEventRecorder {

    private final SecurityEventRepository securityEventRepository;
    private final Clock clock;

    /**
     * Hesapsız olay (bilinmeyen kimlik, geçersiz state vb.) kimliksiz uçlardan sınırsız tetiklenebilir ve denetim değeri
     * taşımaz; bu yüzden veritabanına yazılmaz (sınırsız tablo büyümesi ve yazma yükü engellenir), yalnız türü debug'a
     * yazılır. Hesaba bağlı olaylar kalıcıdır.
     */
    @Transactional
    public void record(SecurityEventType type, UUID accountId) {
        if (accountId == null) {
            log.debug("Security event without account type={}", type);
            return;
        }
        securityEventRepository.save(SecurityEvent.of(type, accountId, MDC.get(RequestIdFilter.MDC_KEY),
                clock.instant()));
    }
}
