package com.ltz.user.domain.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.ltz.user.domain.enums.AccountStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Oyuncu profili (USER-01/02). Kayıt anahtarı Auth hesabının kimliğidir ({@code userId}, REF). Parola, e-posta ve token
 * tutulmaz; kullanıcı adı Auth'tan gelen bir kopyadır. {@code version} iyimser kilittir.
 */
@Entity
@Table(name = "user_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfile {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "username_snapshot", length = 20)
    private String usernameSnapshot;

    @Column(name = "username_normalized", length = 20)
    private String usernameNormalized;

    @Column(name = "display_name", length = 32)
    private String displayName;

    @Column(name = "bio", length = 500)
    private String bio;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "player_types", nullable = false, columnDefinition = "text[]")
    private List<String> playerTypes = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "favorite_genres", nullable = false, columnDefinition = "text[]")
    private List<String> favoriteGenres = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "favorite_platforms", nullable = false, columnDefinition = "text[]")
    private List<String> favoritePlatforms = new ArrayList<>();

    @Column(name = "country", length = 2)
    private String country;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 32)
    private AccountStatus accountStatus;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @Column(name = "onboarding_completed_at")
    private Instant onboardingCompletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * Auth'tan gelen kullanıcı adını yazar. Auth'ta kullanıcı adı değişmediği için ilk değer kalır; farklı bir değer
     * gelirse Auth doğru kabul edilir.
     *
     * @return kayıt değiştiyse {@code true}
     */
    public boolean applyUsername(String username, Instant now) {
        if (username.equals(usernameSnapshot)) {
            return false;
        }
        this.usernameSnapshot = username;
        this.usernameNormalized = username.toLowerCase(Locale.ROOT);
        this.updatedAt = now;
        return true;
    }

    /**
     * Auth'taki hesap durumunu yazar. Eski bir olay yeni durumu ezemez: yalnız daha yeni (ya da eşit olmayan, daha
     * geç) {@code occurredAt} taşıyan olay uygulanır.
     *
     * @return kayıt değiştiyse {@code true}
     */
    public boolean applyStatus(AccountStatus status, Instant occurredAt, Instant now) {
        if (statusChangedAt != null && !occurredAt.isAfter(statusChangedAt)) {
            return false;
        }
        this.statusChangedAt = occurredAt;
        if (status == accountStatus) {
            return true;
        }
        this.accountStatus = status;
        this.updatedAt = now;
        return true;
    }

    /**
     * Profil alanlarını günceller; {@code null} alan "değişmedi" demektir. Değer aynıysa kayıt kirlenmez, bu yüzden
     * değişiklik yoksa {@code version} artmaz.
     *
     * @return kayıt değiştiyse {@code true}
     */
    public boolean update(ProfileChanges changes, Instant now) {
        boolean changed = false;
        if (changes.displayNameSet() && !Objects.equals(displayName, changes.displayName())) {
            this.displayName = changes.displayName();
            changed = true;
        }
        if (changes.bioSet() && !Objects.equals(bio, changes.bio())) {
            this.bio = changes.bio();
            changed = true;
        }
        if (changes.countrySet() && !Objects.equals(country, changes.country())) {
            this.country = changes.country();
            changed = true;
        }
        changed |= replaceList(changes.playerTypes(), playerTypes, list -> this.playerTypes = list);
        changed |= replaceList(changes.favoriteGenres(), favoriteGenres, list -> this.favoriteGenres = list);
        changed |= replaceList(changes.favoritePlatforms(), favoritePlatforms, list -> this.favoritePlatforms = list);
        if (changed) {
            this.updatedAt = now;
        }
        return changed;
    }

    private static boolean replaceList(List<String> incoming, List<String> current,
                                       Consumer<List<String>> setter) {
        if (incoming == null || incoming.equals(current)) {
            return false;
        }
        setter.accept(new ArrayList<>(incoming));
        return true;
    }

    /**
     * {@link #update} girdisi. Metin alanlarında {@code *Set} bayrağı "alan istekte vardı" demektir ({@code null} değer
     * alanı temizler); liste alanlarında {@code null} "değişmedi", boş liste "temizle" demektir.
     */
    public record ProfileChanges(boolean displayNameSet, String displayName, boolean bioSet, String bio,
                                 boolean countrySet, String country, List<String> playerTypes,
                                 List<String> favoriteGenres, List<String> favoritePlatforms) {
    }
}
