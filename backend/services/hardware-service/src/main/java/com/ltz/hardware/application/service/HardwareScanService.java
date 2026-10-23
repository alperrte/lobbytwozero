package com.ltz.hardware.application.service;

import com.ltz.hardware.api.dto.request.PreviewHardwareScanRequest;
import com.ltz.hardware.api.dto.response.HardwareScanPreviewResponse;
import com.ltz.hardware.application.mapper.HardwareScanMapper;
import com.ltz.hardware.application.port.CurrentActor;
import com.ltz.hardware.domain.exception.HardwareNotFoundException;
import com.ltz.hardware.infrastructure.persistence.repository.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HardwareScanService {
    private final CurrentActor actor;
    private final HardwareProfileRepository profiles;
    private final HardwareScanMapper mapper;
    private final HardwareSyncLimits limits;

    public HardwareScanService(CurrentActor actor, HardwareProfileRepository profiles,
            HardwareScanMapper mapper, HardwareSyncLimits limits) {
        this.actor = actor;
        this.profiles = profiles;
        this.mapper = mapper;
        this.limits = limits;
    }

    @Transactional
    public HardwareScanPreviewResponse preview(UUID profileId, PreviewHardwareScanRequest request, String peer) {
        var userId = actor.requireUserId();
        var profile = profiles.findByIdAndUserId(profileId, userId).orElseThrow(HardwareNotFoundException::new);
        if (!Boolean.TRUE.equals(request.processingConsent())) throw new IllegalArgumentException("Açık onay gerekli");
        var scan = mapper.toScan(request.scan());
        scan.validateTime(Instant.now());
        limits.preview(userId, peer);
        return mapper.preview(scan, profile.getVersion());
    }
}
