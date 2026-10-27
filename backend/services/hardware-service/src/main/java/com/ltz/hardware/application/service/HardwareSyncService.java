package com.ltz.hardware.application.service;

import com.ltz.hardware.api.dto.request.ApplyHardwareSyncRequest;
import com.ltz.hardware.api.dto.response.HardwareSyncReceiptResponse;
import com.ltz.hardware.application.mapper.HardwareSyncMapper;
import com.ltz.hardware.application.port.HardwareSyncDeviceAccess;
import com.ltz.hardware.domain.entity.*;
import com.ltz.hardware.domain.exception.*;
import com.ltz.hardware.infrastructure.persistence.repository.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HardwareSyncService {
    private final HardwareProfileRepository profiles;
    private final HardwareSyncConsentRepository consents;
    private final HardwareComponentRepository components;
    private final HardwareSyncDeviceAccess access;
    private final HardwareSyncMapper mapper;
    private final HardwareSyncLimits limits;
    private final HardwareSyncReceiptRepository receipts;

    public HardwareSyncService(HardwareProfileRepository profiles, HardwareSyncConsentRepository consents,
            HardwareComponentRepository components, HardwareSyncDeviceAccess access, HardwareSyncMapper mapper,
            HardwareSyncLimits limits, HardwareSyncReceiptRepository receipts) {
        this.profiles = profiles; this.consents = consents; this.components = components;
        this.access = access; this.mapper = mapper; this.limits = limits; this.receipts = receipts;
    }

    @Transactional
    public HardwareSyncReceiptResponse apply(UUID profileId, UUID key, ApplyHardwareSyncRequest request, String peer) {
        var device = access.requireDevice();
        var profile = profiles.findOwnedForUpdate(profileId, device.userId()).orElseThrow(HardwareNotFoundException::new);
        var state = consents.findByProfileId(profileId).orElseThrow(HardwareConflictException::new);
        state.requireGrant(device.deviceId(), request.consentGeneration());
        limits.apply(device.userId(), device.deviceId(), peer);
        var scan = mapper.scan(request);
        var hash = mapper.fingerprint(profileId, device, request, scan);
        var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        var cached = receipts.findLive(profileId, key, now);
        if (cached.isPresent()) {
            if (!cached.get().requestHash().equals(hash)) throw new HardwareConflictException();
            return mapper.decode(cached.get().responseBody());
        }
        profile.requireVersion(request.profileVersion());
        scan.validateTime(now);
        state.advance(request.sequence(), scan.capturedAt(), now);
        var changes = synchronize(profileId, scan);
        profile.touch();
        profiles.flush();
        var response = new HardwareSyncReceiptResponse(key, profile.getVersion(), request.sequence(),
                scan.capturedAt(), now, scan.components().size(), changes.created(), changes.updated(), changes.removed());
        receipts.store(profileId, key, state.getGeneration(), request.sequence(), hash, mapper.encode(response), now);
        return response;
    }

    private record Changes(int created, int updated, int removed) { }

    private Changes synchronize(UUID profileId, HardwareScan scan) {
        if (components.countByProfileIdAndSource(profileId, "MANUAL") + scan.components().size() > 200) {
            throw new IllegalArgumentException("Profil bileşen sınırı aşıldı");
        }
        var remaining = new HashMap<UUID, HardwareComponent>();
        components.findAllByProfileIdAndSource(profileId, "DESKTOP_SYNC")
                .forEach(c -> remaining.put(c.getSyncComponentKey(), c));
        int created = 0;
        int updated = 0;
        for (var scanned : scan.components()) {
            var existing = remaining.remove(scanned.componentKey());
            if (existing == null) { components.save(HardwareComponent.desktop(profileId, scanned)); created++; }
            else { existing.replaceFromScan(scanned); updated++; }
        }
        components.deleteAll(remaining.values());
        return new Changes(created, updated, remaining.size());
    }
}
