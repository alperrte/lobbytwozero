package com.ltz.game.api.controller;

import com.ltz.game.PostgresTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/v1/game/terms: herkese açık, salt okunur; yalnız ACTIVE + MAIN_GAME oyunlara bağlı terimler. */
@SpringBootTest
class TermControllerTest extends PostgresTestSupport {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.update("delete from game_terms"); jdbc.update("delete from catalog_terms"); jdbc.update("delete from game_external_refs"); jdbc.update("delete from games");
    }
    private UUID game(String title, String type, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into games(id,title,normalized_title,origin,game_type,status,metadata_completeness,created_at,updated_at) values (?,?,?,'EXTERNAL',?,?,'INDEX_ONLY',now(),now())", id, title, title.toLowerCase(), type, status);
        return id;
    }
    private UUID term(String kind, String code, String nameTr, String nameEn) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into catalog_terms(id,kind,code,name_tr,name_en,version) values (?,?,?,?,?,0)", id, kind, code, nameTr, nameEn);
        return id;
    }
    private void link(UUID game, UUID term) { jdbc.update("insert into game_terms(game_id,term_id) values (?,?)", game, term); }

    @Test void listsOnlyTermsOfActiveMainGamesWithCountsOrderedByPopularityThenCodeAndIsPublicAndCacheable() throws Exception {
        UUID a = game("A", "MAIN_GAME", "ACTIVE"), b = game("B", "MAIN_GAME", "ACTIVE"), dlc = game("C", "DLC", "ACTIVE"), archived = game("D", "MAIN_GAME", "ARCHIVED"), unknown = game("E", "UNKNOWN", "ACTIVE");
        UUID rpg = term("GENRE", "role-playing-rpg", null, "Role-playing (RPG)"), adventure = term("GENRE", "adventure", "Macera", "Adventure"), onlyHidden = term("GENRE", "hidden-genre", null, null);
        UUID win = term("PLATFORM", "win", null, "PC (Microsoft Windows)"), tag = term("TAG", "indie-tag", null, null);
        link(a, rpg); link(b, rpg); link(a, adventure); link(dlc, onlyHidden); link(archived, onlyHidden); link(unknown, onlyHidden); link(a, win); link(a, tag);
        mvc.perform(get("/api/v1/game/terms").param("kind", "GENRE")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=300")))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("public")))
                .andExpect(jsonPath("$.kind").value("GENRE")).andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].code").value("role-playing-rpg")).andExpect(jsonPath("$.items[0].gameCount").value(2))
                .andExpect(jsonPath("$.items[0].nameEn").value("Role-playing (RPG)")).andExpect(jsonPath("$.items[0].nameTr").isEmpty())
                .andExpect(jsonPath("$.items[1].code").value("adventure")).andExpect(jsonPath("$.items[1].nameTr").value("Macera"));
        mvc.perform(get("/api/v1/game/terms").param("kind", "PLATFORM")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].code").value("win"));
    }
    @Test void aLimitCapsTheResultAndAnEmptyCatalogueReturnsAnEmptyListNotInventedTerms() throws Exception {
        mvc.perform(get("/api/v1/game/terms").param("kind", "GENRE")).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        UUID a = game("A", "MAIN_GAME", "ACTIVE");
        for (String code : new String[]{"a", "b", "c"}) link(a, term("GENRE", code, null, null));
        mvc.perform(get("/api/v1/game/terms").param("kind", "GENRE").param("limit", "2")).andExpect(jsonPath("$.items.length()").value(2));
    }
    @Test void invalidKindsAndLimitsAreProblemDetailsAndWritesStayClosed() throws Exception {
        for (String kind : new String[]{"CATEGORY", "TAG", "nope", ""}) {
            mvc.perform(get("/api/v1/game/terms").param("kind", kind)).andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        }
        mvc.perform(get("/api/v1/game/terms")).andExpect(status().isBadRequest());
        for (String limit : new String[]{"0", "201", "-1", "x"}) {
            mvc.perform(get("/api/v1/game/terms").param("kind", "GENRE").param("limit", limit)).andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        }
        mvc.perform(post("/api/v1/game/terms").param("kind", "GENRE")).andExpect(status().isForbidden());
    }
}
