package com.ltz.performance.application.service;

import com.ltz.performance.infrastructure.persistence.repository.DeviceLinkRepository;
import com.ltz.performance.infrastructure.persistence.repository.PerformanceAccountAccessGuard;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Diğer servislerin "bu cihaz bu hesaba ait ve Performance'ta aktif mi" sorusunu yanıtlar (yalnız ilişki, kimlik değil). */
@Service
public class DeviceActiveCheckService {
    private final DeviceLinkRepository links;
    private final PerformanceAccountAccessGuard guard;

    public DeviceActiveCheckService(DeviceLinkRepository links, PerformanceAccountAccessGuard guard) {
        this.links = links;
        this.guard = guard;
    }

    @Transactional(readOnly = true)
    public boolean isActiveOwnedDevice(UUID accountId, UUID authDeviceId) {
        guard.requireActive(accountId);
        return links.findByAuthDeviceId(authDeviceId)
                .filter(link -> link.isOwnedBy(accountId) && link.isActive()).isPresent();
    }
}
