package com.ltz.user.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.user.application.mapper.ProfileMapper;
import com.ltz.user.domain.entity.UserProfile;
import com.ltz.user.domain.entity.UserProfile.ProfileChanges;
import com.ltz.user.domain.enums.AccountStatus;
import com.ltz.user.domain.enums.MediaStatus;
import com.ltz.user.domain.exception.UserErrorCode;
import com.ltz.user.domain.exception.UserException;
import com.ltz.user.infrastructure.persistence.repository.UserMediaRepository;
import com.ltz.user.infrastructure.persistence.repository.UserProfileRepository;

import lombok.RequiredArgsConstructor;

/**
 * Oyuncunun kendi profili (USER-01/02). Kimlik her zaman doğrulanmış token'dan gelir; başka kullanıcının profiline
 * yazan bir yol yoktur, bu yüzden IDOR yüzeyi bulunmaz.
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserProfileRepository profiles;
    private final UserMediaRepository media;
    private final Clock clock;

    /**
     * Profili döner; yoksa varsayılan değerlerle oluşturur. Profil normalde Auth'un {@code UserRegistered} olayıyla
     * açılır; olay gecikmiş ya da henüz alıcı açılmamış olsa da kullanıcı kendi profilini görebilsin diye burada da
     * idempotent oluşturulur (eşzamanlı iki çağrıda tek satır oluşur).
     */
    @Transactional
    public ProfileView getOrProvision(UUID userId) {
        return view(loadOrProvision(userId));
    }

    @Transactional
    public ProfileView update(UUID userId, UpdateProfileCommand command) {
        ProfileChanges changes = validate(command);
        UserProfile profile = loadOrProvision(userId);
        if (profile.getVersion() != command.expectedVersion()) {
            throw new UserException(UserErrorCode.CONCURRENT_MODIFICATION);
        }
        if (profile.update(changes, clock.instant())) {
            // Sürüm yazma sırasında artar; yanıtta güncel sürüm dönsün diye (ve eşzamanlı çakışma burada 409 olsun diye)
            // transaction sonunu beklemeden yazılır.
            profiles.flush();
        }
        return view(profile);
    }

    private ProfileView view(UserProfile profile) {
        return ProfileMapper.toView(profile, media.findByUserIdAndStatus(profile.getUserId(), MediaStatus.CURRENT));
    }

    private UserProfile loadOrProvision(UUID userId) {
        return profiles.findById(userId).orElseGet(() -> {
            Instant now = clock.instant();
            profiles.insertIfAbsent(userId, AccountStatus.ACTIVE.name(), null, now);
            return profiles.findById(userId).orElseThrow();
        });
    }

    private static ProfileChanges validate(UpdateProfileCommand command) {
        return new ProfileChanges(
                command.displayName() != null, ProfileText.displayName(command.displayName()),
                command.bio() != null, ProfileText.bio(command.bio()),
                command.country() != null, ProfileText.country(command.country()),
                mapIfPresent(command.playerTypes(), ProfileText::playerTypes),
                mapIfPresent(command.favoriteGenres(), ProfileText::favoriteGenres),
                mapIfPresent(command.favoritePlatforms(), ProfileText::favoritePlatforms));
    }

    private static List<String> mapIfPresent(List<String> raw, UnaryOperator<List<String>> rule) {
        return raw == null ? null : rule.apply(raw);
    }
}
