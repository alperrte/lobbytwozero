package com.ltz.user.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.user.domain.entity.PrivacySettings;
import com.ltz.user.domain.enums.AccountStatus;
import com.ltz.user.domain.exception.UserErrorCode;
import com.ltz.user.domain.exception.UserException;
import com.ltz.user.infrastructure.persistence.repository.PrivacySettingsRepository;
import com.ltz.user.infrastructure.persistence.repository.UserProfileRepository;

import lombok.RequiredArgsConstructor;

/** Kullanıcının kendi gizlilik ayarları (USER-06). Kimlik yalnız token'dan gelir; başkasının ayarına yazan yol yoktur. */
@Service
@RequiredArgsConstructor
public class PrivacyService {

    private final PrivacySettingsRepository settings;
    private final UserProfileRepository profiles;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PrivacySettingsView get(UUID userId) {
        return PrivacySettingsView.of(settings.findById(userId).orElseGet(() -> PrivacySettings.defaults(userId)));
    }

    /**
     * Ayarların tamamını yazar. İlk kayıtta beklenen sürüm 0'dır (varsayılanlar); sonra her değişiklik sürümü artırır.
     * Sürüm uyuşmazsa ya da iki ilk kayıt yarışırsa {@code 409}.
     */
    @Transactional
    public PrivacySettingsView update(UUID userId, UpdatePrivacyCommand command) {
        Instant now = clock.instant();
        PrivacySettings current = settings.findById(userId).orElse(null);
        long version = current == null ? 0 : current.getVersion();
        if (version != command.expectedVersion()) {
            throw new UserException(UserErrorCode.CONCURRENT_MODIFICATION);
        }
        if (current == null) {
            profiles.insertIfAbsent(userId, AccountStatus.ACTIVE.name(), null, now);
            current = PrivacySettings.newRow(userId);
            apply(current, command, now);
            try {
                settings.saveAndFlush(current);
            } catch (DataIntegrityViolationException _) {
                throw new UserException(UserErrorCode.CONCURRENT_MODIFICATION);
            }
            return PrivacySettingsView.of(current);
        }
        if (apply(current, command, now)) {
            settings.flush();
        }
        return PrivacySettingsView.of(current);
    }

    private static boolean apply(PrivacySettings target, UpdatePrivacyCommand command, Instant now) {
        return target.apply(command.friendsList(), command.library(), command.hardware(), command.performance(),
                command.guestProfileAccess(), now);
    }
}
