package com.ltz.game.api.controller;

import com.ltz.game.AuthenticatedApiTest;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountErasureTest extends AuthenticatedApiTest {
    static final UUID ACCOUNT = UUID.randomUUID();
    static final String PATH = "/internal/v1/accounts/" + ACCOUNT;

    @Test void accountWithoutDataAndRepeatedRequestAreNoContent() throws Exception {
        for (int i = 0; i < 2; i++) send(delete(PATH), serviceToken(serviceClaims()), null)
                .andExpect(status().isNoContent()).andExpect(content().string(""));
    }

    @Test void existingPersonalDataIsErasedAndOtherAccountsArePreserved() throws Exception {
        UUID game = game("Erasure test", "INTERNAL", "MAIN_GAME", "ACTIVE");
        jdbc.update("update games set editor_actor_id=? where id=?", ACCOUNT, game);
        send(delete(PATH), serviceToken(serviceClaims()), null).andExpect(status().isNoContent());
        assertNull(jdbc.queryForObject("select editor_actor_id from games where id=?", UUID.class, game));
        jdbc.update("update games set editor_actor_id=? where id=?", DEVELOPER_ID, game);
        UUID other = UUID.randomUUID();
        send(delete("/internal/v1/accounts/" + other), serviceToken(serviceClaims().claim("account_id", other.toString())), null)
                .andExpect(status().isNoContent());
        assertEquals(DEVELOPER_ID, jdbc.queryForObject("select editor_actor_id from games where id=?", UUID.class, game));
    }

    @Test void auditIsAnonymizedBeforeAcknowledgement() throws Exception {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into game_admin_audits(id,actor_id,actor_role,action,reason,created_at) values (?,?,'ADMIN','CREATE_GAME','test audit reason',now())", id, ACCOUNT);
        send(delete(PATH), serviceToken(serviceClaims()), null).andExpect(status().isNoContent());
        assertEquals(new UUID(0, 0), jdbc.queryForObject("select actor_id from game_admin_audits where id=?", UUID.class, id));
        assertNull(jdbc.queryForObject("select reason from game_admin_audits where id=?", String.class, id));
    }

    @Test void moderatorAndAuditTextReferencesAreAlsoDetected() throws Exception {
        UUID game = game("Moderation test", "INTERNAL", "MAIN_GAME", "ACTIVE");
        jdbc.update("insert into game_developer_contents(game_id,description,author_id,status,moderated_by,created_at,updated_at) values (?,'description',?,'VISIBLE',?,now(),now())",
                game, OTHER_DEVELOPER_ID, ACCOUNT);
        send(delete(PATH), serviceToken(serviceClaims()), null).andExpect(status().isNoContent());
        assertNull(jdbc.queryForObject("select moderated_by from game_developer_contents where game_id=?", UUID.class, game));
        jdbc.update("delete from game_developer_contents where game_id=?", game);
        jdbc.update("insert into game_admin_audits(id,actor_id,actor_role,action,details,created_at) values (?,?,'ADMIN','CREATE_GAME',?,now())",
                UUID.randomUUID(), ADMIN_ID, "developer=" + ACCOUNT);
        send(delete(PATH), serviceToken(serviceClaims()), null).andExpect(status().isNoContent());
        assertNull(jdbc.queryForObject("select details from game_admin_audits where actor_id=?", String.class, ADMIN_ID));
    }

    @Test void noTokenAndUserTokensIncludingAdminAreUnauthorized() throws Exception {
        mvc.perform(delete(PATH)).andExpect(status().isUnauthorized());
        for (String value : List.of(admin(), developer(), token(ACCOUNT, "PLAYER")))
            send(delete(PATH), value, null).andExpect(status().isUnauthorized());
    }

    @Test void subjectScopeAndAccountBindingAreRequired() throws Exception {
        for (var bad : List.of(serviceClaims().subject("svc:admin-service"), serviceClaims().claim("scope", null).claim("scp", List.of("account:erase")),
                serviceClaims().claim("scope", "other:scope").claim("roles", List.of("ADMIN")),
                serviceClaims().claim("account_id", null), serviceClaims().claim("account_id", UUID.randomUUID().toString()),
                serviceClaims().claim("account_id", 42)))
            send(delete(PATH), serviceToken(bad), null).andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
    }

    @Test void wrongAudienceTimeAndMissingClaimsAreUnauthorized() throws Exception {
        Instant now = Instant.now();
        for (var bad : List.of(serviceClaims().audience("social-service"), serviceClaims().audience(List.of("game-service", "social-service")),
                serviceClaims().expirationTime(Date.from(now.plusSeconds(61))), serviceClaims().issueTime(null),
                serviceClaims().notBeforeTime(null), serviceClaims().expirationTime(null), serviceClaims().jwtID(null),
                serviceClaims().issueTime(Date.from(now.minusSeconds(150))).expirationTime(Date.from(now.minusSeconds(120)))))
            send(delete(PATH), serviceToken(bad), null).andExpect(status().isUnauthorized());
        send(delete(PATH), new PlainJWT(serviceClaims().build()).serialize(), null).andExpect(status().isUnauthorized());
    }

    @Test void wrongMethodsAndOtherInternalPathsAreDenied() throws Exception {
        send(get(PATH), serviceToken(serviceClaims()), null).andExpect(status().isForbidden());
        send(delete("/internal/v1/other"), serviceToken(serviceClaims()), null).andExpect(status().isForbidden());
        send(delete("/internal/v1/accounts/invalid"), serviceToken(serviceClaims()), null).andExpect(status().isBadRequest());
    }

    @Test void serviceTokenIsRejectedAtPublicAndUserEndpoints() throws Exception {
        for (String path : List.of("/api/v1/game/games", "/api/v1/game/admin/games/" + UUID.randomUUID()))
            send(get(path), serviceToken(serviceClaims()), null).andExpect(status().isUnauthorized());
    }

    @Test void nilIdentityCannotEraseAnonymousAuditActor() throws Exception {
        UUID nil = new UUID(0, 0);
        send(delete("/internal/v1/accounts/" + nil), serviceToken(serviceClaims().claim("account_id", nil.toString())), null)
                .andExpect(status().isBadRequest());
    }

    static JWTClaimsSet.Builder serviceClaims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder().issuer("ltz-auth").audience("game-service").subject("svc:auth-service")
                .issueTime(Date.from(now)).notBeforeTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(30)))
                .jwtID(UUID.randomUUID().toString()).claim("scope", "account:erase").claim("account_id", ACCOUNT.toString());
    }

    static String serviceToken(JWTClaimsSet.Builder claims) throws Exception {
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).type(new JOSEObjectType("svc+jwt")).keyID("game-test-key").build(), claims.build());
        jwt.sign(new RSASSASigner(KEYS.getPrivate()));
        return jwt.serialize();
    }
}
