package com.ltz.lfp.domain.entity;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;

import com.ltz.lfp.domain.enums.ListingStatus;
import com.ltz.lfp.domain.enums.Platform;
import com.ltz.lfp.domain.exception.LfpErrorCode;
import com.ltz.lfp.domain.exception.LfpException;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Oyun arkadaşı ilanı (LFP-01). Sahip ({@code ownerId}) ve oyun ({@code gameId}) başka servislere aittir; yalnız
 * kimlikle (REF) tutulur. Oyun adı, ilan açılırken game-service'ten alınan görüntüleme anlık görüntüsüdür.
 * Her ilanın bir bitişi vardır ({@code expiresAt}, LFP-15); süre ilan açılırken seçilir.
 */
@Entity
@Table(name = "lfp_listings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Listing {

    /** İstemci saati ile sunucu saati arasındaki küçük fark: oyun zamanı bu kadar geçmişte olabilir. */
    static final Duration PLAY_TIME_TOLERANCE = Duration.ofMinutes(5);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "game_id", nullable = false, updatable = false)
    private UUID gameId;

    @Column(name = "game_title_snapshot", nullable = false, length = ListingText.MAX_GAME_TITLE)
    private String gameTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 32)
    private Platform platform;

    @Column(name = "skill_level", length = ListingText.MAX_SKILL_LEVEL)
    private String skillLevel;

    @ElementCollection
    @CollectionTable(name = "lfp_listing_roles", joinColumns = @JoinColumn(name = "listing_id"))
    @OrderColumn(name = "position")
    @Column(name = "role", nullable = false, length = ListingText.MAX_ROLE)
    @BatchSize(size = 50)
    private List<String> roles = new ArrayList<>();

    @Column(name = "players_needed", nullable = false)
    private int playersNeeded;

    @Column(name = "play_time_start")
    private Instant playTimeStart;

    @Column(name = "play_time_end")
    private Instant playTimeEnd;

    @Column(name = "mic_required", nullable = false)
    private boolean micRequired;

    @Column(name = "description", nullable = false, length = ListingText.MAX_DESCRIPTION)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ListingStatus status;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /**
     * Yeni ilan {@code OPEN} durumunda açılır ve {@code now + duration} anında süresi dolar. Süre aralığı uygulama
     * ayarıyla çağıran tarafta denetlenir.
     *
     * @param maxPlayWindow planlanan oyun zamanının en uzun aralığı (başlangıç → bitiş)
     */
    public static Listing open(UUID ownerId, UUID gameId, String gameTitle, ListingContent content, Instant now,
                               Duration duration, Duration maxPlayWindow) {
        Listing listing = new Listing();
        listing.ownerId = ownerId;
        listing.gameId = gameId;
        listing.gameTitle = gameTitle;
        listing.status = ListingStatus.OPEN;
        listing.createdAt = now;
        listing.updatedAt = now;
        listing.expiresAt = now.plus(duration);
        checkPlayTime(content, now.minus(PLAY_TIME_TOLERANCE), listing.expiresAt, maxPlayWindow);
        listing.apply(content);
        return listing;
    }

    public boolean isOwnedBy(UUID userId) {
        return ownerId.equals(userId);
    }

    /** Roller değiştirilemez bir kopya olarak döner; liste yalnız içerik uygulanarak değişir. */
    public List<String> getRoles() {
        return List.copyOf(roles);
    }

    private void apply(ListingContent content) {
        platform = content.platform();
        skillLevel = content.skillLevel();
        roles.clear();
        roles.addAll(content.roles());
        playersNeeded = content.playersNeeded();
        playTimeStart = content.playTimeStart();
        playTimeEnd = content.playTimeEnd();
        micRequired = content.micRequired();
        description = content.description();
    }

    /**
     * LFP-07: oyun zamanı isteğe bağlıdır. Verilirse başlangıç {@code earliest}'ten önce olamaz ve ilan bitmeden
     * başlamalıdır; bitiş yalnız başlangıçla birlikte verilir, başlangıçtan sonradır ve aralık {@code maxWindow}'u
     * aşamaz.
     */
    static void checkPlayTime(ListingContent content, Instant earliest, Instant expiresAt, Duration maxWindow) {
        Instant start = content.playTimeStart();
        Instant end = content.playTimeEnd();
        if (start == null) {
            if (end != null) {
                throw new LfpException(LfpErrorCode.INVALID_PLAY_TIME);
            }
            return;
        }
        if (start.isBefore(earliest)) {
            throw new LfpException(LfpErrorCode.INVALID_PLAY_TIME);
        }
        if (start.isAfter(expiresAt)) {
            throw new LfpException(LfpErrorCode.PLAY_TIME_AFTER_EXPIRY);
        }
        if (end != null && (!end.isAfter(start) || Duration.between(start, end).compareTo(maxWindow) > 0)) {
            throw new LfpException(LfpErrorCode.INVALID_PLAY_TIME);
        }
    }
}
