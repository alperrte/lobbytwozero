package com.ltz.game.infrastructure.security;

import com.ltz.game.application.port.Actor;
import com.ltz.game.domain.enums.ActorRole;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import static org.junit.jupiter.api.Assertions.*;

/** Claim çözümleme tek yerdedir; özel claim adları yapılandırmayla değişir, kod değişmez. */
class LtzJwtAuthenticationConverterTest {
    static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000042");

    static Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("test").header("alg", "RS256").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).claims(map -> map.putAll(claims)).build();
    }

    static LtzJwtAuthenticationConverter converter(String userClaim, String rolesClaim) {
        return new LtzJwtAuthenticationConverter(new JwtSecurityProperties("iss", "aud", null, null, "RS256", userClaim, rolesClaim, Duration.ofSeconds(60)));
    }

    @Test void resolvesActorAndRolesFromDefaultClaims() {
        var authentication = converter("sub", "roles").convert(jwt(Map.of("sub", USER.toString(), "roles", List.of("admin", "ROLE_GAME_DEVELOPER", "PLAYER", 7))));
        var actor = (Actor) authentication.getPrincipal();
        assertEquals(USER, actor.userId());
        assertEquals(Set.of(ActorRole.ADMIN, ActorRole.GAME_DEVELOPER), actor.roles());
        assertEquals(USER.toString(), authentication.getName());
        assertEquals(Set.of("ROLE_ADMIN", "ROLE_GAME_DEVELOPER"),
                authentication.getAuthorities().stream().map(Object::toString).collect(java.util.stream.Collectors.toSet()));
    }

    @Test void claimNamesAreConfigurableForTheFutureAuthContract() {
        var actor = (Actor) converter("uid", "permissions").convert(jwt(Map.of("uid", USER.toString(), "permissions", "MODERATOR ADMIN"))).getPrincipal();
        assertEquals(Set.of(ActorRole.MODERATOR, ActorRole.ADMIN), actor.roles());
    }

    @Test void missingOrMalformedRolesYieldNoRolesAndBadSubjectIsUnauthorized() {
        var converter = converter("sub", "roles");
        assertTrue(((Actor) converter.convert(jwt(Map.of("sub", USER.toString()))).getPrincipal()).roles().isEmpty());
        assertTrue(((Actor) converter.convert(jwt(Map.of("sub", USER.toString(), "roles", Map.of("a", "b")))).getPrincipal()).roles().isEmpty());
        var badSubject = jwt(Map.of("sub", "nope", "roles", "ADMIN"));
        assertThrows(InvalidBearerTokenException.class, () -> converter.convert(badSubject));
        var otherClaimName = converter("uid", "roles");
        var noUserClaim = jwt(Map.of("sub", USER.toString()));
        assertThrows(InvalidBearerTokenException.class, () -> otherClaimName.convert(noUserClaim));
    }

    @Test void actorIsImmutableAndRequiresIdentity() {
        assertThrows(IllegalArgumentException.class, () -> new Actor(null, Set.of()));
        var actor = Actor.of(USER, List.of(ActorRole.ADMIN));
        var roles = actor.roles();
        assertThrows(UnsupportedOperationException.class, () -> roles.add(ActorRole.MODERATOR));
        assertEquals(ActorRole.ADMIN, actor.auditRole(ActorRole.MODERATOR, ActorRole.ADMIN));
        assertThrows(com.ltz.game.domain.exception.AccessForbiddenException.class, () -> actor.auditRole(ActorRole.MODERATOR));
        assertTrue(Actor.of(USER, null).roles().isEmpty());
    }
}
