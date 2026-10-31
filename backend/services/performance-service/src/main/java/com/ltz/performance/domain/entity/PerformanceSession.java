package com.ltz.performance.domain.entity;

import com.ltz.performance.domain.enums.MeasurementSource;
import com.ltz.performance.domain.enums.SessionStatus;
import com.ltz.performance.domain.exception.PerformanceConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

/**
 * Kullanıcının elle başlattığı oyun oturumu. Zamanlar sunucu saatindendir; istemci zamanı kabul edilmez.
 * Oyun ve donanım profili başka servislere ait REF'lerdir; oyun adı yalnız gösterim için kopyalanır.
 */
@Entity
@Table(name = "performance_sessions")
@Getter
public class PerformanceSession {
    public static final int TITLE_MAX = 200;
    /** Bu süreyi aşan ACTIVE oturum terk edilmiş sayılır; bitiş zamanı başlangıç + bu süredir. */
    public static final Duration MAX_ACTIVE = Duration.ofHours(24);

    @Id private UUID id;
    @Column(name = "user_id", nullable = false, updatable = false) private UUID userId;
    @Column(name = "device_link_id", updatable = false) private UUID deviceLinkId;
    @Column(name = "game_id", nullable = false, updatable = false) private UUID gameId;
    @Column(name = "game_title", nullable = false, updatable = false, length = TITLE_MAX) private String gameTitle;
    @Column(name = "hardware_profile_id", updatable = false) private UUID hardwareProfileId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false, length = 16) private MeasurementSource source;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private SessionStatus status;
    @Column(name = "started_at", nullable = false, updatable = false) private Instant startedAt;
    @Column(name = "ended_at") private Instant endedAt;
    @Version private Long version;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected PerformanceSession() { }

    public static PerformanceSession startManual(UUID userId, UUID gameId, String gameTitle, UUID hardwareProfileId,
            UUID deviceLinkId, Instant now) {
        var session = new PerformanceSession();
        session.id = UUID.randomUUID();
        session.userId = Objects.requireNonNull(userId);
        session.gameId = Objects.requireNonNull(gameId);
        session.gameTitle = normalizeTitle(gameTitle);
        session.hardwareProfileId = hardwareProfileId;
        session.deviceLinkId = deviceLinkId;
        session.source = MeasurementSource.MANUAL;
        session.status = SessionStatus.ACTIVE;
        session.startedAt = Objects.requireNonNull(now);
        session.createdAt = now;
        session.updatedAt = now;
        return session;
    }

    /** Başka servisten gelen ad kontrol karakterlerinden arındırılır ve sınırlandırılır. */
    public static String normalizeTitle(String raw) {
        var cleaned = raw == null ? "" : raw.replaceAll("\\p{Cntrl}", " ").strip();
        if (cleaned.isEmpty()) throw new IllegalArgumentException("Game title is required");
        return cleaned.codePointCount(0, cleaned.length()) <= TITLE_MAX ? cleaned
                : cleaned.substring(0, cleaned.offsetByCodePoints(0, TITLE_MAX));
    }

    /**
     * İdempotent: aynı sonlandırma durumu tekrar istenirse değişiklik yapılmaz. Farklı bir sonlandırma durumu
     * mevcut sonucu ezmez, çakışma olarak reddedilir.
     */
    public void end(SessionStatus target, Instant now) {
        if (target == null || target == SessionStatus.ACTIVE) throw new IllegalArgumentException("Invalid end status");
        if (status == target) return;
        if (status != SessionStatus.ACTIVE) {
            throw new PerformanceConflictException(PerformanceConflictException.SESSION_ALREADY_ENDED);
        }
        status = target;
        endedAt = now.isBefore(startedAt) ? startedAt : now;
        updatedAt = now;
    }

    public boolean isActive() { return status == SessionStatus.ACTIVE; }

    public boolean isCompleted() { return status == SessionStatus.COMPLETED; }

    public boolean isOwnedBy(UUID owner) { return userId.equals(owner); }

    public Long durationSeconds() {
        return endedAt == null ? null : Duration.between(startedAt, endedAt).toSeconds();
    }
}
