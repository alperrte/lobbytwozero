package com.ltz.hardware.infrastructure.security;

import com.ltz.hardware.application.port.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityContextHardwareSyncDeviceAccess implements HardwareSyncDeviceAccess {
    private final HardwareSyncDevicePolicy policy;
    public SecurityContextHardwareSyncDeviceAccess(HardwareSyncDevicePolicy policy) { this.policy = policy; }

    @Override public AuthenticatedHardwareDevice requireDevice() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new AuthenticationCredentialsNotFoundException("Cihaz kimliği gerekli");
        if (!(auth.getPrincipal() instanceof AuthenticatedHardwareDevice device)
                || auth.getAuthorities().stream().noneMatch(a -> PERMISSION.equals(a.getAuthority()))) {
            throw new AccessDeniedException("Cihaz yazma izni gerekli");
        }
        HardwareSyncDevicePolicy.requireActiveOwnedDevice(policy, device.userId(), device.deviceId());
        return device;
    }
}
