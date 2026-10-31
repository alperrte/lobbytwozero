package com.ltz.performance.infrastructure.security;

import com.ltz.performance.application.port.AuthenticatedPerformanceDevice;
import com.ltz.performance.application.port.PerformanceSyncDeviceAccess;
import java.util.List;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public final class PerformanceDeviceJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedPerformanceDevice(UUID.fromString(jwt.getClaimAsString("account_id")),
                        UUID.fromString(jwt.getSubject())), null,
                List.of(new SimpleGrantedAuthority(PerformanceSyncDeviceAccess.PERMISSION)));
    }
}
