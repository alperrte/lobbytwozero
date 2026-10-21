package com.ltz.hardware.application.service;

import com.ltz.hardware.api.dto.request.GrantHardwareSyncRequest;
import com.ltz.hardware.api.dto.response.HardwareSyncConsentResponse;
import com.ltz.hardware.application.mapper.HardwareSyncMapper;
import com.ltz.hardware.application.port.*;
import com.ltz.hardware.domain.entity.HardwareSyncConsent;
import com.ltz.hardware.domain.exception.HardwareNotFoundException;
import com.ltz.hardware.infrastructure.persistence.repository.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class HardwareSyncConsentService {
    private final CurrentActor actor;
    private final HardwareProfileRepository profiles;
    private final HardwareSyncConsentRepository consents;
    private final HardwareSyncDevicePolicy policy;
    private final HardwareSyncMapper mapper;
    private final HardwareSyncLimits limits;

    public HardwareSyncConsentService(CurrentActor actor, HardwareProfileRepository profiles,
            HardwareSyncConsentRepository consents, HardwareSyncDevicePolicy policy, HardwareSyncMapper mapper, HardwareSyncLimits limits) {
        this.actor = actor; this.profiles = profiles; this.consents = consents;
        this.policy = policy; this.mapper = mapper; this.limits = limits;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public HardwareSyncConsentResponse get(UUID profileId) {
        var profile = profiles.findByIdAndUserId(profileId, actor.requireUserId()).orElseThrow(HardwareNotFoundException::new);
        return mapper.consent(profile, consents.findByProfileId(profileId).orElse(null));
    }

    @Transactional
    public HardwareSyncConsentResponse grant(UUID profileId, GrantHardwareSyncRequest request, String peer) {
        var userId = actor.requireUserId();
        var profile = profiles.findOwnedForUpdate(profileId, userId).orElseThrow(HardwareNotFoundException::new);
        profile.requireVersion(request.profileVersion());
        if (!Boolean.TRUE.equals(request.accepted())) throw new IllegalArgumentException("Açık onay gerekli");
        HardwareSyncDevicePolicy.requireActiveOwnedDevice(policy, userId, request.deviceId());
        limits.preview(userId, peer);
        var state = consents.findByProfileId(profileId).orElse(null);
        if (state == null) state = consents.save(new HardwareSyncConsent(profileId, request.deviceId()));
        else state.grant(request.deviceId());
        profile.touch();
        profiles.flush();
        return mapper.consent(profile, state);
    }

    @Transactional
    public HardwareSyncConsentResponse revoke(UUID profileId, long version, String peer) {
        var userId = actor.requireUserId();
        var profile = profiles.findOwnedForUpdate(profileId, userId).orElseThrow(HardwareNotFoundException::new);
        profile.requireVersion(version);
        limits.preview(userId, peer);
        var state = consents.findByProfileId(profileId).orElse(null);
        if (state != null && "GRANTED".equals(state.getStatus())) {
            state.revoke();
            profile.touch();
            profiles.flush();
        }
        return mapper.consent(profile, state);
    }
}
