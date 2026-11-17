package com.ltz.hardware.application.service;

import com.ltz.hardware.api.dto.response.HardwarePerformanceSummaryResponse;
import com.ltz.hardware.application.port.CurrentActor;
import com.ltz.hardware.application.port.PerformanceSummaryReader;
import com.ltz.hardware.domain.exception.HardwareNotFoundException;
import com.ltz.hardware.infrastructure.persistence.repository.HardwareProfileRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Dış HTTP sırasında DB transaction/bağlantısı tutulmaz; sahiplik ve silme engeli dönüşte yeniden kontrol edilir. */
@Service
public class HardwarePerformanceService {
    private final CurrentActor actor;
    private final HardwareProfileRepository profiles;
    private final PerformanceSummaryReader performance;

    public HardwarePerformanceService(CurrentActor actor, HardwareProfileRepository profiles, PerformanceSummaryReader performance) {
        this.actor = actor;
        this.profiles = profiles;
        this.performance = performance;
    }

    public HardwarePerformanceSummaryResponse summary(UUID profileId) {
        UUID accountId = actor.requireUserId();
        requireOwned(profileId, accountId);
        var result = performance.read(accountId, profileId);
        requireOwned(profileId, actor.requireUserId());
        return result;
    }

    private void requireOwned(UUID profileId, UUID accountId) {
        if (profiles.findByIdAndUserId(profileId, accountId).isEmpty()) throw new HardwareNotFoundException();
    }
}
