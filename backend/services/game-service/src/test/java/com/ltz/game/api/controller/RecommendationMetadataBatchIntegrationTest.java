package com.ltz.game.api.controller;

import com.ltz.game.AuthenticatedApiTest;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.transaction.TestTransaction;
import static com.ltz.game.api.controller.RecommendationMetadataBatchController.PATH;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecommendationMetadataBatchIntegrationTest extends AuthenticatedApiTest {
    @Test void partitionsIdsInRequestOrderWithoutDistinguishingUnavailableReasons() throws Exception {
        UUID first = eligible("First");
        UUID second = eligible("Second");
        UUID adult = game("Adult", "EXTERNAL", "MAIN_GAME", "ACTIVE");
        UUID unknownAdult = game("Unknown adult", "EXTERNAL", "MAIN_GAME", "ACTIVE");
        UUID archived = game("Archived", "EXTERNAL", "MAIN_GAME", "ARCHIVED");
        UUID merged = game("Merged", "EXTERNAL", "MAIN_GAME", "MERGED");
        UUID delisted = eligible("Delisted");
        UUID unknownType = game("Unknown type", "EXTERNAL", "UNKNOWN", "ACTIVE");
        UUID dlc = game("DLC", "EXTERNAL", "DLC", "ACTIVE");
        UUID missing = UUID.randomUUID();
        jdbc.update("update games set adult=true where id=?", adult);
        jdbc.update("update games set adult=false where id in (?,?,?,?)", archived, merged, unknownType, dlc);
        jdbc.update("update games set delisted_at=now() where id=?", delisted);
        var unavailable = List.of(archived, missing, adult, unknownAdult, delisted, dlc, unknownType, merged);
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                        .content(body(List.of(archived, second, missing, adult, unknownAdult, delisted, dlc, first, unknownType, merged))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].gameId").value(second.toString()))
                .andExpect(jsonPath("$.items[0].gameType").value("MAIN_GAME"))
                .andExpect(jsonPath("$.items[0].adult").value(false))
                .andExpect(jsonPath("$.items[0].visible").value(true))
                .andExpect(jsonPath("$.items[1].gameId").value(first.toString()))
                .andExpect(jsonPath("$.unavailable").value(contains(unavailable.stream().map(UUID::toString).toArray(String[]::new))));
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body(unavailable)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.unavailable.length()").value(unavailable.size()));
    }

    @Test void returnsOnlyRequestedPublicMetadataWithFieldStatesAndRecordedProvenance() throws Exception {
        UUID populated = eligible("Populated");
        UUID sparse = eligible("Sparse");
        term(populated, "GENRE", "rpg");
        term(populated, "GENRE", "action");
        term(populated, "PLATFORM", "windows");
        term(populated, "TAG", "other");
        jdbc.update("""
                insert into game_system_requirements(id,game_id,level,platform,cpu,gpu,ram_mb,storage_mb,os,raw_text)
                values (gen_random_uuid(),?,'MINIMUM','windows','Model A','Model B',8192,12000,'Windows 10','raw'),
                       (gen_random_uuid(),?,'RECOMMENDED','windows','  ',null,0,null,null,'raw')
                """, populated, populated);
        jdbc.update("""
                insert into game_metadata_provenance(game_id,field,source,data_state,recorded_at)
                values (?,'SYSTEM_REQUIREMENTS','STEAM','AVAILABLE',now()), (?,'TITLE','ADMIN','AVAILABLE',now())
                """, populated, sparse);
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body(List.of(populated, sparse))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unavailable").isEmpty())
                .andExpect(jsonPath("$.items[0].genres[*].code").value(contains("action", "rpg")))
                .andExpect(jsonPath("$.items[0].platforms[*].code").value(contains("windows")))
                .andExpect(jsonPath("$.items[0].systemRequirements[0].level").value("MINIMUM"))
                .andExpect(jsonPath("$.items[0].systemRequirements[0].platform").value("windows"))
                .andExpect(jsonPath("$.items[0].systemRequirements[0].cpu.state").value("UNCOMPARABLE"))
                .andExpect(jsonPath("$.items[0].systemRequirements[0].gpu.state").value("UNCOMPARABLE"))
                .andExpect(jsonPath("$.items[0].systemRequirements[0].ramMb.value").value(8192))
                .andExpect(jsonPath("$.items[0].systemRequirements[0].storageMb.value").value(12000))
                .andExpect(jsonPath("$.items[0].systemRequirements[0].os.value").value("Windows 10"))
                .andExpect(jsonPath("$.items[0].systemRequirements[0].vramMb.state").value("UNKNOWN"))
                .andExpect(jsonPath("$.items[0].systemRequirements[0].driver.state").value("UNKNOWN"))
                .andExpect(jsonPath("$.items[0].systemRequirements[0].rawText").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.items[0].systemRequirements[1].level").value("RECOMMENDED"))
                .andExpect(jsonPath("$.items[0].systemRequirements[1].cpu.value").value(nullValue()))
                .andExpect(jsonPath("$.items[0].systemRequirements[1].gpu.state").value("UNKNOWN"))
                .andExpect(jsonPath("$.items[0].systemRequirements[1].ramMb.state").value("UNKNOWN"))
                .andExpect(jsonPath("$.items[0].systemRequirements[1].storageMb.state").value("UNKNOWN"))
                .andExpect(jsonPath("$.items[0].systemRequirements[1].os.state").value("UNKNOWN"))
                .andExpect(jsonPath("$.items[0].requirementsProvenance.source").value("STEAM"))
                .andExpect(jsonPath("$.items[0].requirementsProvenance.verifiedAt").value(nullValue()))
                .andExpect(jsonPath("$.items[1].genres").isEmpty())
                .andExpect(jsonPath("$.items[1].platforms").isEmpty())
                .andExpect(jsonPath("$.items[1].systemRequirements").isEmpty())
                .andExpect(jsonPath("$.items[1].requirementsProvenance").value(nullValue()));
    }

    @Test void acceptsOnlyThePublicJsonPostAndDoesNotTrustIdentityHeaders() throws Exception {
        UUID adult = eligible("Adult");
        jdbc.update("update games set adult=true where id=?", adult);
        String payload = body(List.of(adult));
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(payload).header("X-Role", "ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(post(PATH).contentType(MediaType.TEXT_PLAIN).content(payload)).andExpect(status().isForbidden());
        mvc.perform(post(PATH + "/").contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isForbidden());
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(payload).header("Authorization", "Bearer bad"))
                .andExpect(status().isUnauthorized());
    }

    @Test void readsWithoutAnExistingTransaction() throws Exception {
        TestTransaction.end();
        UUID missing = UUID.randomUUID();
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body(List.of(missing))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unavailable[0]").value(missing.toString()));
    }

    @Test void consumesTheSameRateBudgetAsCardBatch() throws Exception {
        String payload = body(List.of(UUID.randomUUID()));
        for (int i = 0; i < 100; i++) {
            mvc.perform(BatchProtectionTest.from(post(PATH).contentType(MediaType.APPLICATION_JSON).content(payload), "198.51.100.95"))
                    .andExpect(status().isOk());
        }
        var card = post(GameBatchControllerTest.PATH).contentType(MediaType.APPLICATION_JSON).content(payload);
        mvc.perform(BatchProtectionTest.from(card, "198.51.100.95"))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }

    private UUID eligible(String title) {
        UUID id = game(title, "EXTERNAL", "MAIN_GAME", "ACTIVE");
        jdbc.update("update games set adult=false where id=?", id);
        return id;
    }

    private void term(UUID gameId, String kind, String code) {
        UUID termId = UUID.randomUUID();
        jdbc.update("insert into catalog_terms(id,kind,code) values (?,?,?)", termId, kind, code);
        jdbc.update("insert into game_terms(game_id,term_id) values (?,?)", gameId, termId);
    }

    private static String body(List<UUID> ids) {
        return ids.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(",", "{\"gameIds\":[", "]}"));
    }
}
