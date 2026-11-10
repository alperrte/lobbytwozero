package com.ltz.user.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.user.domain.entity.UserConsents;
import com.ltz.user.domain.enums.AccountStatus;
import com.ltz.user.domain.exception.UserErrorCode;
import com.ltz.user.domain.exception.UserException;
import com.ltz.user.infrastructure.persistence.repository.UserConsentsRepository;
import com.ltz.user.infrastructure.persistence.repository.UserProfileRepository;

import lombok.RequiredArgsConstructor;

/**
 * Kullanıcının kendi rızaları (LIB-14). Kimlik yalnız token'dan gelir; başkasının rızasına yazan yol yoktur. Rıza
 * geri çekilince satır silinmez, {@code false} olur. Kararlar her istekte DB'den okunur (önbellek yok): geri çekme bir
 * sonraki karar isteğinde etkilidir.
 */
@Service
@RequiredArgsConstructor
public class ConsentsService {

    private final UserConsentsRepository consents;
    private final UserProfileRepository profiles;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ConsentsView get(UUID userId) {
        return ConsentsView.of(consents.findById(userId).orElseGet(() -> UserConsents.defaults(userId)));
    }

    /**
     * Rızaların tamamını yazar. İlk kayıtta beklenen sürüm 0'dır (rıza yok); sonra her değişiklik sürümü artırır.
     * Sürüm uyuşmazsa ya da iki ilk kayıt yarışırsa {@code 409}. Değişiklik yoksa sürüm ve zaman değişmez.
     */
    @Transactional
    public ConsentsView update(UUID userId, UpdateConsentsCommand command) {
        Instant now = clock.instant();
        UserConsents current = consents.findById(userId).orElse(null);
        long version = current == null ? 0 : current.getVersion();
        if (version != command.expectedVersion()) {
            throw new UserException(UserErrorCode.CONCURRENT_MODIFICATION);
        }
        if (current == null) {
            profiles.insertIfAbsent(userId, AccountStatus.ACTIVE.name(), null, now);
            current = UserConsents.newRow(userId, now);
            current.applyRecommendation(command.recommendation(), now);
            try {
                consents.saveAndFlush(current);
            } catch (DataIntegrityViolationException _) {
                throw new UserException(UserErrorCode.CONCURRENT_MODIFICATION);
            }
            return ConsentsView.of(current);
        }
        if (current.applyRecommendation(command.recommendation(), now)) {
            consents.flush();
        }
        return ConsentsView.of(current);
    }
}
