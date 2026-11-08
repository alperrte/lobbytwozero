package com.ltz.game.api.controller;

import com.ltz.game.PostgresTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Detay API'si kullanıcının diline uygun açıklamayı döndürür; Türkçe yoksa İngilizce ve gerçek dil bilgisi; DB (oyun, dil) tekliğini ve dil biçimini zorlar. */
@SpringBootTest(properties = {"ltz.game.pricing.sync.enabled=false", "ltz.game.rating.sync.enabled=false"})
class GameLocalizedDescriptionApiTest extends PostgresTestSupport {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    MockMvc mvc;
    UUID withTurkish, englishOnly;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.update("delete from game_external_refs");
        jdbc.update("delete from games");
        withTurkish = game("A", "English summary");
        englishOnly = game("B", "Another English summary");
        jdbc.update("insert into game_localized_descriptions(id,game_id,language,description,source,fetched_at) values (?,?,'tr','Türkçe açıklama için bir metin.','STEAM',now())", UUID.randomUUID(), withTurkish);
    }

    private UUID game(String title, String description) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into games(id,title,normalized_title,origin,game_type,description,created_at,updated_at) values (?,?,?,'EXTERNAL','MAIN_GAME',?,now(),now())", id, title, title.toLowerCase(java.util.Locale.ROOT), description);
        jdbc.update("insert into game_metadata_provenance(game_id,field,source,data_state,recorded_at) values (?,'DESCRIPTION','IGDB','AVAILABLE',now())", id);
        return id;
    }

    @Test void turkishUserGetsTheTurkishTextAndEnglishUserAndDefaultGetTheOriginal() throws Exception {
        mvc.perform(get("/api/v1/game/games/" + withTurkish).param("lang", "tr")).andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Türkçe açıklama için bir metin.")).andExpect(jsonPath("$.descriptionLanguage").value("tr"));
        mvc.perform(get("/api/v1/game/games/" + withTurkish).param("lang", "en")).andExpect(jsonPath("$.description").value("English summary")).andExpect(jsonPath("$.descriptionLanguage").value("en"));
        mvc.perform(get("/api/v1/game/games/" + withTurkish)).andExpect(jsonPath("$.description").value("English summary")).andExpect(jsonPath("$.descriptionLanguage").value("en"));
        mvc.perform(get("/api/v1/game/games/" + withTurkish).param("lang", "TR")).andExpect(jsonPath("$.descriptionLanguage").value("tr"));
    }

    @Test void withoutATurkishTextTheEnglishFallbackAndItsRealLanguageAreReturned() throws Exception {
        mvc.perform(get("/api/v1/game/games/" + englishOnly).param("lang", "tr")).andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Another English summary")).andExpect(jsonPath("$.descriptionLanguage").value("en"));
        mvc.perform(get("/api/v1/game/games/" + englishOnly).param("lang", "de")).andExpect(jsonPath("$.descriptionLanguage").value("en"));
    }

    @Test void malformedLanguageIsRejectedAndOtherDetailFieldsAreUnchanged() throws Exception {
        for (String bad : new String[]{"turkish", "t", "tr-TR!", "1", "t r"}) {
            mvc.perform(get("/api/v1/game/games/" + withTurkish).param("lang", bad)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/game/games/" + withTurkish).param("lang", "tr")).andExpect(jsonPath("$.game.gameId").value(withTurkish.toString()))
                .andExpect(jsonPath("$.metadata.gameId").value(withTurkish.toString())).andExpect(jsonPath("$.freshness.status").exists());
    }

    @Test void theDatabaseRejectsDuplicatesMalformedLanguagesBlankTextAndRemovesRowsWithTheGame() {
        assertThrows(DataAccessException.class, () -> jdbc.update("insert into game_localized_descriptions(id,game_id,language,description,source,fetched_at) values (?,?,'tr','Başka metin.','STEAM',now())", UUID.randomUUID(), withTurkish));
        assertThrows(DataAccessException.class, () -> jdbc.update("insert into game_localized_descriptions(id,game_id,language,description,source,fetched_at) values (?,?,'TURKISH','x','STEAM',now())", UUID.randomUUID(), englishOnly));
        assertThrows(DataAccessException.class, () -> jdbc.update("insert into game_localized_descriptions(id,game_id,language,description,source,fetched_at) values (?,?,'tr','   ','STEAM',now())", UUID.randomUUID(), englishOnly));
        jdbc.update("insert into game_localized_descriptions(id,game_id,language,description,source,fetched_at) values (?,?,'de','Deutscher Text.','STEAM',now())", UUID.randomUUID(), withTurkish);
        jdbc.update("delete from games where id=?", withTurkish);
        org.junit.jupiter.api.Assertions.assertEquals(0L, jdbc.queryForObject("select count(*) from game_localized_descriptions", Long.class), "oyun silinince satırları da silinir");
    }
}
