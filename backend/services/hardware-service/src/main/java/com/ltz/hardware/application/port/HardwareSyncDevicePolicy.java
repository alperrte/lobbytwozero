package com.ltz.hardware.application.port;

import com.ltz.hardware.domain.exception.HardwareNotFoundException;
import java.util.UUID;

public interface HardwareSyncDevicePolicy {
    boolean isActiveOwnedDevice(UUID userId, UUID deviceId);

    static void requireActiveOwnedDevice(HardwareSyncDevicePolicy policy, UUID userId, UUID deviceId) {
        boolean allowed;
        try { allowed = policy.isActiveOwnedDevice(userId, deviceId); }
        catch (RuntimeException _) { allowed = false; }
        if (!allowed) throw new HardwareNotFoundException();
    }
}
