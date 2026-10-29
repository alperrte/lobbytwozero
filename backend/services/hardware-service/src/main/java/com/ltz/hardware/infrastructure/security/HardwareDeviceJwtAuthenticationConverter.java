package com.ltz.hardware.infrastructure.security;

import com.ltz.hardware.application.port.AuthenticatedHardwareDevice;
import com.ltz.hardware.application.port.HardwareSyncDeviceAccess;
import java.util.List;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public final class HardwareDeviceJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedHardwareDevice(UUID.fromString(jwt.getClaimAsString("account_id")),
                        UUID.fromString(jwt.getSubject())), null,
                List.of(new SimpleGrantedAuthority(HardwareSyncDeviceAccess.PERMISSION)));
    }
}
