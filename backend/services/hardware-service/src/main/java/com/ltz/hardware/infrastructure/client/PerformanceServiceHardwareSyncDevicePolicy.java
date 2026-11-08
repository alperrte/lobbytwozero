package com.ltz.hardware.infrastructure.client;

import com.ltz.hardware.application.port.HardwareSyncDevicePolicy;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Her grant/apply için hesaba bağlı servis token'ıyla güncel Performance cihaz kararı alınır. */
@Component
public class PerformanceServiceHardwareSyncDevicePolicy implements HardwareSyncDevicePolicy {
    private final HardwareClientProperties properties;
    private final HardwareServiceTokenClient tokens;
    private final HardwareHttpClient http;

    public PerformanceServiceHardwareSyncDevicePolicy(HardwareClientProperties properties,
            HardwareServiceTokenClient tokens, HardwareHttpClient http) {
        this.properties = properties;
        this.tokens = tokens;
        this.http = http;
    }

    @Override public boolean isActiveOwnedDevice(UUID userId, UUID deviceId) {
        if (userId == null || deviceId == null) return false;
        try {
            return http.activeDevice(properties.performanceServiceUrl().resolve(
                    "/internal/v1/accounts/" + userId + "/devices/" + deviceId), tokens.deviceVerificationToken(userId));
        } catch (RuntimeException _) {
            return false;
        }
    }
}
