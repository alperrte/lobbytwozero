package com.ltz.performance.infrastructure.security;

import com.ltz.performance.application.port.AuthenticatedPerformanceDevice;
import com.ltz.performance.application.port.PerformanceSyncDeviceAccess;
import com.ltz.performance.infrastructure.persistence.repository.PerformanceAccountAccessGuard;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityContextPerformanceSyncDeviceAccess implements PerformanceSyncDeviceAccess {
    private final PerformanceAccountAccessGuard guard;

    public SecurityContextPerformanceSyncDeviceAccess(PerformanceAccountAccessGuard guard) { this.guard = guard; }

    @Override
    public AuthenticatedPerformanceDevice requireDevice() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Cihaz kimliği gerekli");
        }
        if (!(auth.getPrincipal() instanceof AuthenticatedPerformanceDevice device)
                || auth.getAuthorities().stream().noneMatch(a -> PERMISSION.equals(a.getAuthority()))) {
            throw new AccessDeniedException("Cihaz yazma izni gerekli");
        }
        guard.requireActive(device.userId());
        return device;
    }
}
