package com.ltz.hardware.application.mapper;

import com.ltz.hardware.api.dto.request.ApplyHardwareSyncRequest;
import com.ltz.hardware.api.dto.response.*;
import com.ltz.hardware.application.port.AuthenticatedHardwareDevice;
import com.ltz.hardware.domain.entity.*;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class HardwareSyncMapper {
    private final ObjectMapper json;
    private final HardwareScanMapper scans;
    public HardwareSyncMapper(ObjectMapper json, HardwareScanMapper scans) { this.json = json; this.scans = scans; }

    public HardwareScan scan(ApplyHardwareSyncRequest request) { return scans.toScan(request.scan()); }

    private record Fingerprint(UUID profileId, UUID userId, UUID deviceId, UUID generation,
            long profileVersion, long sequence, HardwareScan scan) { }

    public String fingerprint(UUID profileId, AuthenticatedHardwareDevice device,
            ApplyHardwareSyncRequest request, HardwareScan scan) {
        return HardwareSyncDigest.hash(json.writeValueAsString(new Fingerprint(profileId, device.userId(), device.deviceId(),
                request.consentGeneration(), request.profileVersion(), request.sequence(), scan)));
    }

    public String encode(HardwareSyncReceiptResponse response) { return json.writeValueAsString(response); }
    public HardwareSyncReceiptResponse decode(String body) { return json.readValue(body, HardwareSyncReceiptResponse.class); }

    public HardwareSyncConsentResponse consent(HardwareProfile profile, HardwareSyncConsent state) {
        if (state == null) return new HardwareSyncConsentResponse(profile.getVersion(), "NOT_GRANTED", null, null, null, null, 0, null, null);
        return new HardwareSyncConsentResponse(profile.getVersion(), state.getStatus(), state.getDeviceId(), state.getGeneration(),
                state.getGrantedAt(), state.getRevokedAt(), state.getLastSequence(), state.getLastCapturedAt(), state.getLastSyncedAt());
    }
}
