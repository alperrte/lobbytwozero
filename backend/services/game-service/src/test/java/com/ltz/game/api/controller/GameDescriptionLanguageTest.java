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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Açıklama dili: sağlayıcı (IGDB/Steam) açıklaması "en", yönetici metni bildirdiği dil, açıklama yoksa null; açıklama çevrilmez. */
@SpringBootTest(properties = {"ltz.game.pricing.sync.enabled=false", "ltz.game.rating.sync.enabled=false"})
class GameDescriptionLanguageTest extends PostgresTestSupport {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.update("delete from game_external_refs");
        jdbc.update("delete from games");
    }

    private UUID game(String title, String description, String provenanceSource, String metadataLanguage) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into games(id,title,normalized_title,origin,game_type,description,metadata_language,created_at,updated_at) values (?,?,?,'EXTERNAL','MAIN_GAME',?,?,now(),now())",
                id, title, title.toLowerCase(java.util.Locale.ROOT), description, metadataLanguage);
        if (provenanceSource != null) {
            jdbc.update("insert into game_metadata_provenance(game_id,field,source,data_state,recorded_at) values (?,'DESCRIPTION',?,'AVAILABLE',now())", id, provenanceSource);
        }
        return id;
    }

    @Test void providerDescriptionsAreEnglishAdminTextUsesItsDeclaredLanguageAndMissingDescriptionHasNone() throws Exception {
        UUID igdb = game("A", "English summary", "IGDB", null), steam = game("B", "Steam text", "STEAM", null);
        UUID admin = game("C", "Türkçe açıklama", "ADMIN", "TR"), adminUnknown = game("D", "Bir açıklama", "ADMIN", null), none = game("E", null, null, null);
        mvc.perform(get("/api/v1/game/games/" + igdb)).andExpect(status().isOk()).andExpect(jsonPath("$.descriptionLanguage").value("en")).andExpect(jsonPath("$.description").value("English summary"));
        mvc.perform(get("/api/v1/game/games/" + steam)).andExpect(jsonPath("$.descriptionLanguage").value("en"));
        mvc.perform(get("/api/v1/game/games/" + admin)).andExpect(jsonPath("$.descriptionLanguage").value("tr"));
        mvc.perform(get("/api/v1/game/games/" + adminUnknown)).andExpect(jsonPath("$.descriptionLanguage").isEmpty());
        mvc.perform(get("/api/v1/game/games/" + none)).andExpect(jsonPath("$.descriptionLanguage").isEmpty()).andExpect(jsonPath("$.description").isEmpty());
    }
}
