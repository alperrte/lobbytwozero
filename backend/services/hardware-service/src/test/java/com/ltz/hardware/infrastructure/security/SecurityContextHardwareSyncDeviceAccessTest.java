package com.ltz.hardware.infrastructure.security;

import static org.mockito.Mockito.mock;

import com.ltz.hardware.application.port.AuthenticatedActor;
import com.ltz.hardware.application.port.AuthenticatedHardwareDevice;
import com.ltz.hardware.application.port.HardwareSyncDeviceAccess;
import com.ltz.hardware.domain.exception.HardwareNotFoundException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityContextHardwareSyncDeviceAccessTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void requiresVerifiedDeviceScopeAndActiveOwnershipAtServiceBoundary() {
        var owner = UUID.randomUUID(); var device = UUID.randomUUID();
        var principal = new AuthenticatedHardwareDevice(owner, device);
        var scope = List.of(new SimpleGrantedAuthority(HardwareSyncDeviceAccess.PERMISSION));
        var access = new SecurityContextHardwareSyncDeviceAccess((user, id) -> owner.equals(user) && device.equals(id), mock(com.ltz.hardware.infrastructure.persistence.repository.HardwareAccountAccessGuard.class));
        assertThatThrownBy(access::requireDevice).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.unauthenticated(principal, null));
        assertThatThrownBy(access::requireDevice).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new AuthenticatedActor(owner), null, scope));
        assertThatThrownBy(access::requireDevice).isInstanceOf(AccessDeniedException.class);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        assertThatThrownBy(access::requireDevice).isInstanceOf(AccessDeniedException.class);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, scope));
        assertThat(access.requireDevice()).isEqualTo(principal);
        var unavailable = new SecurityContextHardwareSyncDeviceAccess(new UnavailableHardwareSyncDevicePolicy(), mock(com.ltz.hardware.infrastructure.persistence.repository.HardwareAccountAccessGuard.class));
        assertThatThrownBy(unavailable::requireDevice).isInstanceOf(HardwareNotFoundException.class);
    }
}
