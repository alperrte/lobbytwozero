package com.ltz.user.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.user.domain.entity.UserPreferences;
import com.ltz.user.domain.entity.UserProfile;
import com.ltz.user.domain.entity.UserProfile.ProfileChanges;
import com.ltz.user.domain.enums.Theme;
import com.ltz.user.domain.exception.UserErrorCode;
import com.ltz.user.domain.exception.UserException;
import com.ltz.user.infrastructure.persistence.repository.UserPreferencesRepository;
import com.ltz.user.infrastructure.persistence.repository.UserProfileRepository;

import lombok.RequiredArgsConstructor;

/**
 * Yeni üyenin ilk kurulum akışı (USER-09): favori oyun türleri ve platformları (zorunlu, en az birer tane), isteğe bağlı
 * oyuncu türü, dil ve tema. Seçimler Faz 1 profil alanlarına ve USER-10 tercihlerine yazılır; ayrı bir onboarding tablosu
 * yoktur. Tamamlama <b>idempotenttir</b>: kurulum zaten tamamlandıysa hiçbir şey değişmez ve güncel durum döner (seçimleri
 * sonradan değiştirmek profil ve tercih uçlarının işidir). Kimlik yalnız token'dan gelir.
 */
@Service
@RequiredArgsConstructor
public class OnboardingService {

    static final String MISSING_GENRES = "FAVORITE_GENRES";
    static final String MISSING_PLATFORMS = "FAVORITE_PLATFORMS";

    private final ProfileService profiles;
    private final UserProfileRepository profileRepository;
    private final UserPreferencesRepository preferences;
    private final Clock clock;

    @Transactional
    public OnboardingView status(UUID userId) {
        return view(profiles.loadOrProvision(userId));
    }

    @Transactional
    public OnboardingView complete(UUID userId, CompleteOnboardingCommand command) {
        UserProfile profile = profiles.loadOrProvision(userId);
        if (profile.isOnboardingCompleted()) {
            return view(profile);
        }
        if (profile.getVersion() != command.expectedVersion()) {
            throw new UserException(UserErrorCode.CONCURRENT_MODIFICATION);
        }
        List<String> genres = ProfileText.favoriteGenres(orEmpty(command.favoriteGenres()));
        List<String> platforms = ProfileText.favoritePlatforms(orEmpty(command.favoritePlatforms()));
        if (genres.isEmpty() || platforms.isEmpty()) {
            throw new UserException(UserErrorCode.ONBOARDING_INCOMPLETE);
        }
        List<String> playerTypes = command.playerTypes() == null ? null : ProfileText.playerTypes(command.playerTypes());
        String locale = command.locale() == null ? null : PreferencesService.normalizeLocale(command.locale());

        Instant now = clock.instant();
        profile.update(new ProfileChanges(false, null, false, null, false, null, playerTypes, genres, platforms), now);
        profile.completeOnboarding(now);
        writePreferences(userId, locale, command.theme(), now);
        // Sürüm yazma sırasında artar; yanıtta güncel sürüm dönsün diye transaction sonunu beklemeden yazılır.
        profileRepository.flush();
        return view(profile);
    }

    /** Dil/tema seçildiyse tercihleri yazar; diğer tercih alanları mevcut değerinde kalır (satır yoksa varsayılan). */
    private void writePreferences(UUID userId, String locale, Theme theme, Instant now) {
        if (locale == null && theme == null) {
            return;
        }
        UserPreferences current = preferences.findById(userId).orElse(null);
        boolean exists = current != null;
        UserPreferences target = exists ? current : UserPreferences.newRow(userId);
        target.apply(locale == null ? target.getLocale() : locale, theme == null ? target.getTheme() : theme,
                target.isReducedMotion(), target.isCompactLayout(), now);
        if (!exists) {
            preferences.save(target);
        }
    }

    private static OnboardingView view(UserProfile profile) {
        List<String> missing = new ArrayList<>();
        if (!profile.isOnboardingCompleted()) {
            if (profile.getFavoriteGenres().isEmpty()) {
                missing.add(MISSING_GENRES);
            }
            if (profile.getFavoritePlatforms().isEmpty()) {
                missing.add(MISSING_PLATFORMS);
            }
        }
        return new OnboardingView(profile.isOnboardingCompleted(), profile.getOnboardingCompletedAt(),
                List.copyOf(missing), profile.getVersion());
    }

    private static List<String> orEmpty(List<String> list) {
        return list == null ? List.of() : list;
    }
}
