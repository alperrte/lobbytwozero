package com.ltz.auth.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.auth.application.port.LoginDeviceContext;
import com.ltz.auth.application.port.SecurityNotificationType;
import com.ltz.auth.domain.entity.Account;
import com.ltz.auth.infrastructure.persistence.repository.KnownDeviceRepository;
import com.ltz.auth.infrastructure.security.SecureTokens;

import lombok.RequiredArgsConstructor;

/**
 * "Yeni cihazdan giriş" bildirimi. Başarılı oturum açılışında tarayıcının cihaz çerezi hesabın tanıdığı cihazlarda
 * aranır (yalnız SHA-256 özeti; IP ve User-Agent saklanmaz):
 * <ul>
 *   <li>tanınıyorsa yalnız son görülme yenilenir;</li>
 *   <li>yeniyse kaydedilir; hesabın hiç tanınan cihazı yoksa (ilk giriş) bildirim gitmez;</li>
 *   <li>aksi halde, hesap için son 24 saatte bu türden mail gitmediyse, {@link SecurityNotificationType#SUSPICIOUS_LOGIN}
 *       commit sonrası hesabın e-postasına gider (e-postasız hesapta dispatcher atlar).</li>
 * </ul>
 * Hesap başına en çok {@value #MAX_DEVICES} cihaz tutulur, en eski budanır.
 */
@Service
@RequiredArgsConstructor
public class KnownDeviceService {

    static final int MAX_DEVICES = 20;
    static final Duration NOTIFY_INTERVAL = Duration.ofHours(24);

    private final KnownDeviceRepository repository;
    private final LoginDeviceContext deviceContext;
    private final SecurityNotificationDispatcher notifier;
    private final SecureTokens secureTokens;
    private final Clock clock;

    /** Çağıranın işlemine katılır: oturum geri alınırsa cihaz kaydı ve bildirim de gitmez. */
    @Transactional
    public void recordLogin(Account account) {
        String token = deviceContext.deviceToken().orElse(null);
        if (token == null) {
            return;
        }
        UUID accountId = account.getId();
        String hash = secureTokens.sha256Hex(token);
        Instant now = clock.instant();
        if (repository.touch(accountId, hash, now) > 0) {
            return;
        }
        boolean firstDevice = repository.countByAccountId(accountId) == 0;
        if (repository.insertIfAbsent(UUID.randomUUID(), accountId, hash, now) == 0) {
            return; // Aynı tarayıcıdan eşzamanlı başka giriş kaydı ekledi.
        }
        repository.pruneBeyond(accountId, MAX_DEVICES);
        if (firstDevice || repository.existsByAccountIdAndNotifiedAtAfter(accountId, now.minus(NOTIFY_INTERVAL))) {
            return;
        }
        repository.markNotified(accountId, hash, now);
        notifier.notifyAfterCommit(accountId, account.getEmail(), SecurityNotificationType.SUSPICIOUS_LOGIN);
    }
}
