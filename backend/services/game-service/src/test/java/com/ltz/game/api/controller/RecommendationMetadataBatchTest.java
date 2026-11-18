package com.ltz.game.api.controller;

import com.ltz.game.AuthenticatedApiTest;
import com.nimbusds.jwt.JWTClaimsSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** REC-06/16: Recommendation toplu teknik metadata ucu (internal, hesaba bağlı game-metadata:read). */
class RecommendationMetadataBatchTest extends AuthenticatedApiTest {
    static final String PATH = "/internal/v1/recommendation/game-metadata/batch";
    static final UUID ACCOUNT = UUID.randomUUID();

    private static JWTClaimsSet.Builder claims() {
        return AccountErasureTest.serviceClaims().subject("svc:recommendation-service")
                .claim("scope", "game-metadata:read").claim("account_id", ACCOUNT.toString());
    }

    private static String body(UUID... ids) {
        return "{\"gameIds\":[" + String.join(",", java.util.Arrays.stream(ids).map(id -> "\"" + id + "\"").toList()) + "]}";
    }

    private void term(UUID game, String kind, String code) {
        UUID term = UUID.randomUUID();
        jdbc.update("insert into catalog_terms(id,kind,code) values (?,?,?) on conflict (kind,code) do nothing", term, kind, code);
        UUID id = jdbc.queryForObject("select id from catalog_terms where kind=? and code=?", UUID.class, kind, code);
        jdbc.update("insert into game_terms(game_id,term_id) values (?,?)", game, id);
    }

    @Test void returnsGenresPlatformsAndLevelSeparatedRequirementsInOneCall() throws Exception {
        UUID game = game("Meta batch", "INTERNAL", "MAIN_GAME", "ACTIVE");
        term(game, "GENRE", "rpg");
        term(game, "GENRE", "action");
        term(game, "PLATFORM", "pc");
        jdbc.update("insert into game_system_requirements(id,game_id,level,platform,os,cpu,gpu,ram_mb,storage_mb) "
                + "values (?,?,'MINIMUM','windows','Windows 10','Intel i5','GTX 960',8192,50000)", UUID.randomUUID(), game);
        jdbc.update("insert into game_system_requirements(id,game_id,level,platform,cpu) values (?,?,'RECOMMENDED','windows','Intel i7')",
                UUID.randomUUID(), game);
        UUID missing = UUID.randomUUID();
        send(post(PATH).contentType("application/json"), serviceToken(claims()), body(game, missing))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].gameId").value(game.toString()))
                .andExpect(jsonPath("$.items[0].gameType").value("MAIN_GAME"))
                .andExpect(jsonPath("$.items[0].genres[0]").value("action")).andExpect(jsonPath("$.items[0].genres[1]").value("rpg"))
                .andExpect(jsonPath("$.items[0].platforms[0]").value("pc"))
                .andExpect(jsonPath("$.items[0].requirements.length()").value(2))
                .andExpect(jsonPath("$.items[0].requirements[0].level").value("MINIMUM"))
                .andExpect(jsonPath("$.items[0].requirements[0].ramMb").value(8192))
                .andExpect(jsonPath("$.items[0].requirements[1].level").value("RECOMMENDED"))
                .andExpect(jsonPath("$.items[0].requirements[1].ramMb").doesNotExist())
                .andExpect(jsonPath("$.unavailable[0]").value(missing.toString()));
    }

    @Test void inactiveDelistedAdultAndUnknownAreIndistinguishablyUnavailable() throws Exception {
        UUID inactive = game("Meta inactive", "INTERNAL", "MAIN_GAME", "ARCHIVED");
        UUID delisted = game("Meta delisted", "INTERNAL", "MAIN_GAME", "ACTIVE");
        jdbc.update("update games set delisted_at=now() where id=?", delisted);
        UUID adult = game("Meta adult", "INTERNAL", "MAIN_GAME", "ACTIVE");
        jdbc.update("update games set adult=true where id=?", adult);
        UUID unknownAdult = game("Meta adult unknown", "INTERNAL", "MAIN_GAME", "ACTIVE");
        UUID missing = UUID.randomUUID();
        send(post(PATH).contentType("application/json"), serviceToken(claims()), body(inactive, delisted, adult, unknownAdult, missing))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].gameId").value(unknownAdult.toString()))
                .andExpect(jsonPath("$.items[0].genres.length()").value(0)).andExpect(jsonPath("$.items[0].requirements.length()").value(0))
                .andExpect(jsonPath("$.unavailable.length()").value(4));
    }

    @Test void invalidBodiesAreRejected() throws Exception {
        UUID id = UUID.randomUUID();
        List<String> bad = List.of("{}", "{\"gameIds\":[]}", "{\"gameIds\":[null]}", "{\"gameIds\":[\"x\"]}", body(id, id),
                "{\"gameIds\":[" + String.join(",", java.util.stream.IntStream.range(0, 101).mapToObj(i -> "\"" + UUID.randomUUID() + "\"").toList()) + "]}");
        for (String value : bad)
            send(post(PATH).contentType("application/json"), serviceToken(claims()), value).andExpect(status().isBadRequest());
    }

    @Test void tokenRulesAreEnforced() throws Exception {
        mvc.perform(post(PATH).contentType("application/json").content(body(UUID.randomUUID()))).andExpect(status().isUnauthorized());
        for (String value : List.of(admin(), developer(), token(ACCOUNT, "PLAYER")))
            send(post(PATH).contentType("application/json"), value, body(UUID.randomUUID())).andExpect(status().isUnauthorized());
        for (var wrong : List.of(claims().claim("scope", "account:erase"), claims().subject("svc:library-service"),
                claims().claim("account_id", null),
                claims().claim("scope", "game-identity:decision:read")))
            send(post(PATH).contentType("application/json"), serviceToken(wrong), body(UUID.randomUUID())).andExpect(status().isForbidden());
        send(post(PATH).contentType("application/json"), serviceToken(claims().audience("user-service")), body(UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        send(get(PATH), serviceToken(claims()), null).andExpect(status().isForbidden());
    }

    private static String serviceToken(JWTClaimsSet.Builder builder) throws Exception { return AccountErasureTest.serviceToken(builder); }
}
