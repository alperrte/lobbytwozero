package com.ltz.performance.application.service;

import com.ltz.performance.api.dto.request.StartSessionRequest;
import com.ltz.performance.api.dto.response.DeletedSessionsResponse;
import com.ltz.performance.api.dto.response.SessionPageResponse;
import com.ltz.performance.api.dto.response.SessionResponse;
import com.ltz.performance.application.port.GameCatalog;
import com.ltz.performance.application.port.HardwareProfileDirectory;
import com.ltz.performance.domain.entity.DeviceLink;
import com.ltz.performance.domain.entity.PerformanceSession;
import com.ltz.performance.domain.enums.SessionStatus;
import com.ltz.performance.domain.exception.PerformanceConflictException;
import com.ltz.performance.domain.exception.PerformanceNotFoundException;
import com.ltz.performance.domain.exception.PerformanceUnprocessableException;
import com.ltz.performance.infrastructure.persistence.repository.DeviceLinkRepository;
import com.ltz.performance.infrastructure.persistence.repository.PerformanceAccountAccessGuard;
import com.ltz.performance.infrastructure.persistence.repository.PerformanceSessionMaintenance;
import com.ltz.performance.infrastructure.persistence.repository.PerformanceSessionRepository;
import com.ltz.performance.infrastructure.persistence.repository.PerformanceWriteLimits;
import com.ltz.performance.infrastructure.persistence.repository.SessionSpecifications;
import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Elle başlatılan/bitirilen oyun oturumları. Oyun ve donanım profili başka servislerin sözleşmeleriyle doğrulanır
 * (bu servis onların veritabanına erişmez); bu çağrılar veritabanı işleminin dışındadır (bağlantı havuzu küçüktür).
 * Hesap kilidi ({@link PerformanceAccountAccessGuard}) aynı kullanıcının eşzamanlı başlat/bitir/sil işlemlerini
 * sıraya dizer.
 */
@Service
public class PerformanceSessionService {
    /** Hesap başına saklanan en çok oturum; silme fonksiyonunun tek turda temizleyebildiği sayıdır. */
    public static final int MAX_STORED_SESSIONS = 1000;
    public static final int MAX_PAGE_SIZE = 50;

    private final PerformanceSessionRepository sessions;
    private final PerformanceSessionMaintenance maintenance;
    private final DeviceLinkRepository links;
    private final GameCatalog games;
    private final HardwareProfileDirectory hardware;
    private final PerformanceAccountAccessGuard guard;
    private final PerformanceWriteLimits limits;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public PerformanceSessionService(PerformanceSessionRepository sessions, PerformanceSessionMaintenance maintenance,
            DeviceLinkRepository links, GameCatalog games, HardwareProfileDirectory hardware,
            PerformanceAccountAccessGuard guard, PerformanceWriteLimits limits, TransactionTemplate transaction,
            Clock clock) {
        this.sessions = sessions;
        this.maintenance = maintenance;
        this.links = links;
        this.games = games;
        this.hardware = hardware;
        this.guard = guard;
        this.limits = limits;
        this.transaction = transaction;
        this.clock = clock;
    }

    public SessionResponse start(UUID userId, String authorization, StartSessionRequest request, String peer) {
        limits.session(userId, peer);
        var game = games.find(request.gameId())
                .orElseThrow(() -> new PerformanceUnprocessableException(PerformanceUnprocessableException.GAME_NOT_FOUND));
        if (request.hardwareProfileId() != null
                && !hardware.isOwnedProfile(authorization, request.hardwareProfileId())) {
            throw new PerformanceUnprocessableException(PerformanceUnprocessableException.HARDWARE_PROFILE_NOT_FOUND);
        }
        return transaction.execute(_ -> {
            guard.requireActive(userId);
            var now = clock.instant();
            requireUsableLink(userId, request.deviceLinkId());
            maintenance.abandonStale(userId, now);
            if (sessions.existsByUserIdAndStatus(userId, SessionStatus.ACTIVE)) {
                throw new PerformanceConflictException(PerformanceConflictException.ACTIVE_SESSION_EXISTS);
            }
            if (sessions.countByUserId(userId) >= MAX_STORED_SESSIONS) {
                throw new PerformanceConflictException(PerformanceConflictException.SESSION_LIMIT_REACHED);
            }
            var session = PerformanceSession.startManual(userId, game.gameId(), game.title(),
                    request.hardwareProfileId(), request.deviceLinkId(), now);
            return SessionResponse.from(sessions.saveAndFlush(session));
        });
    }

    /**
     * İdempotent: aynı bitiş durumu tekrar istenirse mevcut sonuç döner; farklı bir bitiş durumu 409 verir.
     * Yabancı ve olmayan oturum aynı şekilde 404'tür.
     */
    public SessionResponse end(UUID userId, UUID sessionId, SessionStatus target, String peer) {
        limits.session(userId, peer);
        return transaction.execute(_ -> {
            guard.requireActive(userId);
            var now = clock.instant();
            maintenance.abandonStale(userId, now);
            var session = sessions.findByIdAndUserId(sessionId, userId).orElseThrow(PerformanceNotFoundException::new);
            session.end(target, now);
            return SessionResponse.from(sessions.saveAndFlush(session));
        });
    }

    @Transactional(readOnly = true)
    public SessionResponse get(UUID userId, UUID sessionId) {
        guard.requireActive(userId);
        return sessions.findByIdAndUserId(sessionId, userId).map(SessionResponse::from)
                .orElseThrow(PerformanceNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public SessionResponse active(UUID userId) {
        guard.requireActive(userId);
        return sessions.findFirstByUserIdAndStatus(userId, SessionStatus.ACTIVE)
                .filter(s -> s.getStartedAt().plus(PerformanceSession.MAX_ACTIVE).isAfter(clock.instant()))
                .map(SessionResponse::from).orElseThrow(PerformanceNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public SessionPageResponse list(UUID userId, SessionFilter filter, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) throw new IllegalArgumentException("Invalid page");
        guard.requireActive(userId);
        // Sıra sabittir (en yeni başlayan önce, eşitlikte id): sayfalar arasında kayma/yinelenme olmaz.
        var sort = Sort.by(Sort.Order.desc("startedAt"), Sort.Order.desc("id"));
        var result = sessions.findAll(SessionSpecifications.matching(userId, filter), PageRequest.of(page, size, sort));
        return new SessionPageResponse(result.getContent().stream().map(SessionResponse::from).toList(), page, size,
                result.hasNext(), result.getTotalElements());
    }

    /** Raporu veritabanı CASCADE ile birlikte siler. Sahiplik sorguda; tekrar çağrı 404'tür. */
    public void delete(UUID userId, UUID sessionId, String peer) {
        limits.session(userId, peer);
        transaction.executeWithoutResult(_ -> {
            guard.requireActive(userId);
            if (sessions.deleteOwned(sessionId, userId) == 0) throw new PerformanceNotFoundException();
        });
    }

    /** Çağıranın tüm oturum ve raporlarını siler; boş kümede 0 döner (idempotent). */
    public DeletedSessionsResponse deleteAll(UUID userId, String peer) {
        limits.session(userId, peer);
        return transaction.execute(_ -> {
            guard.requireActive(userId);
            return new DeletedSessionsResponse(maintenance.deleteAll(userId));
        });
    }

    private void requireUsableLink(UUID userId, UUID linkId) {
        if (linkId == null) return;
        var usable = links.findByIdAndUserId(linkId, userId).filter(DeviceLink::isActive).isPresent();
        if (!usable) throw new PerformanceUnprocessableException(PerformanceUnprocessableException.DEVICE_LINK_NOT_FOUND);
    }
}
