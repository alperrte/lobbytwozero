package com.ltz.hardware.infrastructure.security;

import com.ltz.hardware.application.port.HardwareSyncDevicePolicy;
import java.util.UUID;

/** Açıkça seçilen kapalı fallback; üretim bean'i PerformanceServiceHardwareSyncDevicePolicy'dir. */
public class UnavailableHardwareSyncDevicePolicy implements HardwareSyncDevicePolicy {
    @Override public boolean isActiveOwnedDevice(UUID userId, UUID deviceId) { return false; }
}
