package com.ltz.game.infrastructure.security;

import com.ltz.game.application.port.Actor;
import com.ltz.game.domain.enums.ActorRole;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

/**
 * Claim yapısının tek çözümleme noktası. Auth Service sözleşmesi netleştiğinde değişiklik yalnız buradadır.
 * Rol adları büyük/küçük harfe duyarsızdır, isteğe bağlı {@code ROLE_} öneki atılır, bilinmeyen roller yok sayılır.
 */
@Component
public class LtzJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final JwtSecurityProperties properties;

    public LtzJwtAuthenticationConverter(JwtSecurityProperties properties) { this.properties = properties; }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new ActorAuthentication(new Actor(userId(jwt), roles(jwt)), jwt);
    }

    private UUID userId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getClaimAsString(properties.userIdClaim()));
        } catch (RuntimeException _) {
            throw new InvalidBearerTokenException("Geçersiz kullanıcı kimliği.");
        }
    }

    private Set<ActorRole> roles(Jwt jwt) {
        Object claim = jwt.getClaim(properties.rolesClaim());
        Set<ActorRole> roles = EnumSet.noneOf(ActorRole.class);
        if (claim instanceof Collection<?> values) values.forEach(value -> add(roles, value));
        else if (claim instanceof String text) for (String value : text.split("[\\s,]+")) add(roles, value);
        return roles;
    }

    private static void add(Set<ActorRole> roles, Object value) {
        if (!(value instanceof String text)) return;
        String name = text.strip().toUpperCase(Locale.ROOT);
        if (name.startsWith("ROLE_")) name = name.substring(5);
        for (ActorRole role : ActorRole.values()) if (role.name().equals(name)) roles.add(role);
    }
}
