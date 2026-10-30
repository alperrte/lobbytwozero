package com.ltz.user.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.user.domain.entity.UserPreferences;
import com.ltz.user.domain.enums.AccountStatus;
import com.ltz.user.domain.exception.UserErrorCode;
import com.ltz.user.domain.exception.UserException;
import com.ltz.user.infrastructure.persistence.repository.UserPreferencesRepository;
import com.ltz.user.infrastructure.persistence.repository.UserProfileRepository;

import lombok.RequiredArgsConstructor;

/** Kullanıcının kendi tercihleri (USER-10). Kimlik yalnız token'dan gelir; başkasının tercihine yazan yol yoktur. */
@Service
@RequiredArgsConstructor
public class PreferencesService {

    private final UserPreferencesRepository preferences;
    private final UserProfileRepository profiles;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PreferencesView get(UUID userId) {
        return PreferencesView.of(preferences.findById(userId).orElseGet(() -> UserPreferences.defaults(userId)));
    }

    /**
     * Tercihlerin tamamını yazar. İlk kayıtta beklenen sürüm 0'dır (varsayılanlar); sonra her değişiklik sürümü artırır.
     * Sürüm uyuşmazsa ya da iki ilk kayıt yarışırsa {@code 409}.
     */
    @Transactional
    public PreferencesView update(UUID userId, UpdatePreferencesCommand command) {
        String locale = normalizeLocale(command.locale());
        Instant now = clock.instant();
        UserPreferences current = preferences.findById(userId).orElse(null);
        long version = current == null ? 0 : current.getVersion();
        if (version != command.expectedVersion()) {
            throw new UserException(UserErrorCode.CONCURRENT_MODIFICATION);
        }
        if (current == null) {
            profiles.insertIfAbsent(userId, AccountStatus.ACTIVE.name(), null, now);
            current = UserPreferences.newRow(userId);
            apply(current, command, locale, now);
            try {
                preferences.saveAndFlush(current);
            } catch (DataIntegrityViolationException _) {
                throw new UserException(UserErrorCode.CONCURRENT_MODIFICATION);
            }
            return PreferencesView.of(current);
        }
        if (apply(current, command, locale, now)) {
            preferences.flush();
        }
        return PreferencesView.of(current);
    }

    /** Onboarding dahil tercih yazan yollar için: dili doğrular ve küçük harfe çevirir. */
    static String normalizeLocale(String raw) {
        String locale = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
        if (!UserPreferences.LOCALES.contains(locale)) {
            throw new UserException(UserErrorCode.INVALID_LOCALE);
        }
        return locale;
    }

    private static boolean apply(UserPreferences target, UpdatePreferencesCommand command, String locale, Instant now) {
        return target.apply(locale, command.theme(), command.reducedMotion(), command.compactLayout(), now);
    }
}
