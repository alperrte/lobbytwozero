package com.ltz.game.api.controller;

import com.ltz.game.AuthenticatedApiTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GAME-15: Library/Review için kimliksiz, küçük ve yalnız herkese açık toplu kart okuması. */
@Import(AuthenticatedApiTest.Fakes.class)
class GameBatchControllerTest extends AuthenticatedApiTest {
    static final String PATH = "/api/v1/game/games/batch";

    String body(Object... ids) {
        List<String> values = new ArrayList<>();
        for (Object id : ids) values.add(id == null ? "null" : "\"" + id + "\"");
        return "{\"gameIds\":[" + String.join(",", values) + "]}";
    }

    @Test void returnsPublicCardsInRequestOrderAndListsUnavailableIdsConsistently() throws Exception {
        UUID internal = game("Internal", "INTERNAL", "MAIN_GAME", "ACTIVE");
        UUID external = game("External", "EXTERNAL", "MAIN_GAME", "ACTIVE");
        UUID dlc = game("Extra content", "EXTERNAL", "DLC", "ACTIVE");
        UUID archived = game("Old", "INTERNAL", "MAIN_GAME", "ARCHIVED");
        UUID merged = game("Merged", "INTERNAL", "MAIN_GAME", "MERGED");
        UUID missing = UUID.randomUUID();
        jdbc.update("insert into game_external_refs(id,game_id,provider,external_id) values (gen_random_uuid(),?,?,?)", external, "STEAM", "12345");
        jdbc.update("insert into game_media(id,game_id,type,url,sort_order) values (gen_random_uuid(),?,'COVER','https://cdn.example/c.jpg',0)", internal);

        mvc.perform(post(PATH).contentType("application/json").content(body(external, missing, internal, archived, dlc, merged)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].gameId").value(external.toString()))
                .andExpect(jsonPath("$.items[0].origin").value("EXTERNAL"))
                .andExpect(jsonPath("$.items[0].externalReferences[0].provider").value("STEAM"))
                .andExpect(jsonPath("$.items[0].externalReferences[0].externalId").value("12345"))
                .andExpect(jsonPath("$.items[1].gameId").value(internal.toString()))
                .andExpect(jsonPath("$.items[1].coverUrl").value("https://cdn.example/c.jpg"))
                .andExpect(jsonPath("$.items[1].externalReferences").isEmpty())
                .andExpect(jsonPath("$.items[2].gameId").value(dlc.toString()))
                .andExpect(jsonPath("$.items[2].gameType").value("DLC"))
                .andExpect(jsonPath("$.unavailable.length()").value(3))
                .andExpect(jsonPath("$.unavailable[0]").value(missing.toString()))
                .andExpect(jsonPath("$.unavailable[1]").value(archived.toString()))
                .andExpect(jsonPath("$.unavailable[2]").value(merged.toString()));
    }

    @Test void cardsMatchTheSingleCardContractAndNeverExposeAdminOrUserData() throws Exception {
        UUID id = game("Contract", "INTERNAL", "MAIN_GAME", "ACTIVE");
        jdbc.update("update games set editor_rank=1,editor_actor_id=?,editor_evidence='Gizli operatör gerekçesi' where id=?", ADMIN_ID, id);
        String single = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/game/" + id + "/card"))
                .andReturn().getResponse().getContentAsString();
        String batch = mvc.perform(post(PATH).contentType("application/json").content(body(id))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(com.jayway.jsonpath.JsonPath.read(single, "$").toString(), com.jayway.jsonpath.JsonPath.read(batch, "$.items[0]").toString());
        for (String forbidden : List.of("version", "status", "editor", "actor", "evidence", "rating", "library", "favorite", "userId", "authorId"))
            assertFalse(batch.toLowerCase().contains(forbidden.toLowerCase()), forbidden);
    }

    @Test void duplicatesAreCollapsedAndRequestsAreBounded() throws Exception {
        UUID id = game("Once", "INTERNAL", "MAIN_GAME", "ACTIVE");
        mvc.perform(post(PATH).contentType("application/json").content(body(id, id, id))).andExpect(jsonPath("$.items.length()").value(1));
        Object[] fifty = new Object[50];
        for (int i = 0; i < 50; i++) fifty[i] = UUID.randomUUID();
        mvc.perform(post(PATH).contentType("application/json").content(body(fifty))).andExpect(status().isOk()).andExpect(jsonPath("$.unavailable.length()").value(50));
        Object[] fiftyOne = new Object[51];
        for (int i = 0; i < 51; i++) fiftyOne[i] = UUID.randomUUID();
        mvc.perform(post(PATH).contentType("application/json").content(body(fiftyOne))).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        // 51 kimlik tekrarlarla 50'ye indirilse bile istek boyutu sınırı istek düzeyindedir.
        Object[] repeated = new Object[51];
        java.util.Arrays.fill(repeated, id);
        mvc.perform(post(PATH).contentType("application/json").content(body(repeated))).andExpect(status().isBadRequest());
    }

    @Test void invalidBodiesAreRejected() throws Exception {
        for (String invalid : List.of("{}", "{\"gameIds\":null}", "{\"gameIds\":[]}", body((Object) null), "{\"gameIds\":[\"not-a-uuid\"]}",
                "{\"gameIds\":\"" + UUID.randomUUID() + "\"}", "{\"gameIds\":[],\"extra\":1}", "{\"ids\":[\"" + UUID.randomUUID() + "\"]}", "[]", "", "not json"))
            mvc.perform(post(PATH).contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
        mvc.perform(post(PATH).content("{\"gameIds\":[\"" + UUID.randomUUID() + "\"]}")).andExpect(status().is4xxClientError());
    }

    @Test void worksAnonymouslyAndIgnoresAnyTokenOrIdentityHeaders() throws Exception {
        UUID id = game("Public", "INTERNAL", "MAIN_GAME", "ACTIVE");
        mvc.perform(post(PATH).contentType("application/json").content(body(id)).header("X-User-Id", ADMIN_ID.toString()).header("X-Role", "ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
        // Geçersiz bir token herkese açık uçta bile reddedilir; geçerli token ek bilgi açmaz.
        mvc.perform(post(PATH).contentType("application/json").content(body(id)).header("Authorization", "Bearer garbage")).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).contentType("application/json").content(body(id)).header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test void csrfExemptionAppliesOnlyToTheAnonymousBatchRead() throws Exception {
        // Aynı yola farklı yöntem, benzer yol, kardeş yazma yolları ve korumalı yollar hâlâ CSRF/yetki ile reddedilir.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(PATH).contentType("application/json").content(body(UUID.randomUUID())))
                .andExpect(status().isForbidden());
        mvc.perform(post(PATH + "x").contentType("application/json").content(body(UUID.randomUUID()))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/game/games").contentType("application/json").content(body(UUID.randomUUID()))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/game/admin/games").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
    }

    /** Review yazma doğrulaması (GameServiceClient): tek kimlik sorulur; var ise items=[o], unavailable=[]; yoksa items=[], unavailable=[kimlik]. */
    @Test void singleIdAnswersKeepTheShapeReviewValidationDependsOn() throws Exception {
        UUID existing = game("Review Target", "EXTERNAL", "MAIN_GAME", "ACTIVE");
        jdbc.update("insert into game_external_refs(id,game_id,provider,external_id) values (gen_random_uuid(),?,'STEAM','777')", existing);
        jdbc.update("insert into game_media(id,game_id,type,url,sort_order) values (gen_random_uuid(),?,'COVER','https://cdn.example/r.jpg',0)", existing);
        mvc.perform(post(PATH).contentType("application/json").content(body(existing))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].gameId").value(existing.toString()))
                .andExpect(jsonPath("$.items[0].title").value("Review Target"))
                .andExpect(jsonPath("$.items[0].coverUrl").value("https://cdn.example/r.jpg"))
                .andExpect(jsonPath("$.items[0].externalReferences[0].provider").value("STEAM"))
                .andExpect(jsonPath("$.unavailable").isArray()).andExpect(jsonPath("$.unavailable").isEmpty());
        UUID missing = UUID.randomUUID();
        // "Oyun yok" 404 değil, 200 + unavailable ile anlatılır; Review bunu 404'e çevirir, 5xx/429'u 503'e.
        mvc.perform(post(PATH).contentType("application/json").content(body(missing))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray()).andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.unavailable.length()").value(1)).andExpect(jsonPath("$.unavailable[0]").value(missing.toString()));
    }

    /** Keşif listeleri +18 ve kaldırılmış oyunu gizler; batch gizlemez (Review sayfaları, incelemelerim ve yazma doğrulaması bunlara dayanır). */
    @Test void adultAndDelistedGamesStayReadableThroughBatch() throws Exception {
        UUID adult = game("Adult Title", "EXTERNAL", "MAIN_GAME", "ACTIVE");
        UUID delisted = game("Delisted Title", "EXTERNAL", "MAIN_GAME", "ACTIVE");
        jdbc.update("update games set adult=true where id=?", adult);
        jdbc.update("update games set delisted_at=now() where id=?", delisted);
        mvc.perform(post(PATH).contentType("application/json").content(body(adult, delisted))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].gameId").value(adult.toString()))
                .andExpect(jsonPath("$.items[1].gameId").value(delisted.toString()))
                .andExpect(jsonPath("$.unavailable").isEmpty());
    }

    @Test void answersFiftyIdsInOneRequest() throws Exception {
        UUID[] ids = new UUID[50];
        for (int i = 0; i < 50; i++) {
            ids[i] = game("Batch " + i, i % 2 == 0 ? "INTERNAL" : "EXTERNAL", "MAIN_GAME", "ACTIVE");
            jdbc.update("insert into game_external_refs(id,game_id,provider,external_id) values (gen_random_uuid(),?,'STEAM',?)", ids[i], "" + (100 + i));
        }
        mvc.perform(post(PATH).contentType("application/json").content(body((Object[]) ids))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(50)).andExpect(jsonPath("$.unavailable").isEmpty())
                .andExpect(jsonPath("$.items[49].externalReferences[0].externalId").value("149"));
    }
}
