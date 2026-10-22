package com.ltz.game.infrastructure.security;

import com.ltz.game.application.port.Actor;
import com.ltz.game.domain.enums.ActorRole;
import java.util.List;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

/**
 * Auth sözleşmesindeki sub ve roles listesini Actor'a dönüştürür.
 * Yalnız birebir eşleşen domain rolleri yetki verir; PLAYER/CONTENT_CREATOR ek domain yetkisi taşımaz.
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
            String subject = jwt.getSubject();
            UUID userId = UUID.fromString(subject);
            if (!userId.toString().equalsIgnoreCase(subject)) throw new IllegalArgumentException();
            return userId;
        } catch (RuntimeException _) {
            throw new InvalidBearerTokenException("Geçersiz kullanıcı kimliği.");
        }
    }

    private Set<ActorRole> roles(Jwt jwt) {
        Object claim = jwt.getClaim(properties.rolesClaim());
        Set<ActorRole> roles = EnumSet.noneOf(ActorRole.class);
        if (claim instanceof List<?> values && values.stream().allMatch(String.class::isInstance))
            values.forEach(value -> add(roles, value));
        return roles;
    }

    private static void add(Set<ActorRole> roles, Object value) {
        if (!(value instanceof String text)) return;
        for (ActorRole role : ActorRole.values()) if (role.name().equals(text)) roles.add(role);
    }
}
