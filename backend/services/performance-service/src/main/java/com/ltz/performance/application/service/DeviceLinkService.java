package com.ltz.performance.application.service;

import com.ltz.performance.api.dto.response.DeviceLinkResponse;
import com.ltz.performance.application.port.AuthDeviceDirectory;
import com.ltz.performance.domain.entity.DeviceLink;
import com.ltz.performance.domain.enums.DeviceLinkStatus;
import com.ltz.performance.domain.exception.PerformanceConflictException;
import com.ltz.performance.domain.exception.PerformanceNotFoundException;
import com.ltz.performance.infrastructure.persistence.repository.DeviceLinkRepository;
import com.ltz.performance.infrastructure.persistence.repository.PerformanceAccountAccessGuard;
import com.ltz.performance.infrastructure.persistence.repository.PerformanceWriteLimits;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cihaz kimliği, sahiplik ve iptal Auth Service'tedir. Burada yalnız kullanıcının açıkça başlattığı cihaz↔performans
 * ilişkisi tutulur; bağlama isteğinde Auth'a çağıranın kendi Bearer'ı ile sorulur ve Auth'un listesinde olmayan
 * cihaz asla bağlanmaz. Auth çağrısı veritabanı işleminin dışındadır (bağlantı havuzu küçüktür).
 */
@Service
public class DeviceLinkService {
    /** Auth'un hesap başına en fazla aktif cihaz sayısı ile aynıdır; bağlantı sayısı bunu aşamaz. */
    public static final int MAX_ACTIVE_LINKS = 5;

    private final DeviceLinkRepository links;
    private final AuthDeviceDirectory authDevices;
    private final PerformanceAccountAccessGuard guard;
    private final PerformanceWriteLimits limits;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public DeviceLinkService(DeviceLinkRepository links, AuthDeviceDirectory authDevices,
            PerformanceAccountAccessGuard guard, PerformanceWriteLimits limits, TransactionTemplate transaction,
            Clock clock) {
        this.links = links;
        this.authDevices = authDevices;
        this.guard = guard;
        this.limits = limits;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<DeviceLinkResponse> list(UUID userId) {
        guard.requireActive(userId);
        return links.findAllByUserIdOrderByLinkedAtAsc(userId).stream().map(DeviceLinkResponse::from).toList();
    }

    /** İdempotent: aynı cihazı tekrar bağlamak aynı kaydı döner; devre dışı kayıt yeniden etkinleştirilir. */
    public DeviceLinkResponse link(UUID userId, String authorization, UUID authDeviceId, String peer) {
        limits.link(userId, peer);
        var device = authDevices.findUsableDevice(authorization, authDeviceId)
                .orElseThrow(PerformanceNotFoundException::new);
        return transaction.execute(_ -> {
            guard.requireActive(userId);
            var now = clock.instant();
            var existing = links.findByAuthDeviceId(authDeviceId).orElse(null);
            if (existing != null && !existing.isOwnedBy(userId)) {
                throw new PerformanceConflictException(PerformanceConflictException.DEVICE_LINK_CONFLICT);
            }
            if (existing != null && existing.isActive()) {
                existing.activate(device.label(), now);
                return DeviceLinkResponse.from(links.save(existing));
            }
            requireCapacity(userId);
            if (existing == null) {
                return DeviceLinkResponse.from(links.save(DeviceLink.create(authDeviceId, userId, device.label(), now)));
            }
            existing.activate(device.label(), now);
            return DeviceLinkResponse.from(links.save(existing));
        });
    }

    /** İdempotent: kayıt silinmez; devre dışı kayıt yeniden devre dışı bırakılırsa değişmez. */
    public DeviceLinkResponse disable(UUID userId, UUID linkId, String peer) {
        limits.link(userId, peer);
        return transaction.execute(_ -> {
            guard.requireActive(userId);
            var link = links.findByIdAndUserId(linkId, userId).orElseThrow(PerformanceNotFoundException::new);
            link.disable(clock.instant());
            return DeviceLinkResponse.from(links.save(link));
        });
    }

    private void requireCapacity(UUID userId) {
        if (links.countByUserIdAndStatus(userId, DeviceLinkStatus.ACTIVE) >= MAX_ACTIVE_LINKS) {
            throw new PerformanceConflictException(PerformanceConflictException.DEVICE_LINK_LIMIT_REACHED);
        }
    }
}
