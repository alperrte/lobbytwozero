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

/** DLC ↔ ana oyun ilişkisi API'si: detay alanı (geriye uyumlu), sayfalı DLC listesi, sıralama, 404/400 ve katalogda DLC gizliliği. */
@SpringBootTest(properties = {"ltz.game.pricing.sync.enabled=false", "ltz.game.rating.sync.enabled=false"})
class GameDlcRelationTest extends PostgresTestSupport {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    MockMvc mvc;
    UUID main, newest, older, undated, orphan, archived, demo;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.update("delete from game_external_refs");
        jdbc.update("delete from games");
        main = game("Main Game", "MAIN_GAME", null, null);
        newest = game("Dlc Newest", "DLC", main, "2026-05-01");
        older = game("Dlc Older", "DLC", main, "2024-01-01");
        undated = game("Dlc Undated", "DLC", main, null);
        archived = game("Dlc Archived", "DLC", main, "2025-01-01");
        jdbc.update("update games set status='ARCHIVED' where id=?", archived);
        orphan = game("Dlc Without Parent", "DLC", null, "2025-06-01");
        demo = game("A Demo", "DEMO", null, null);
    }

    private UUID game(String title, String type, UUID parent, String release) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into games(id,title,normalized_title,origin,game_type,release_date,parent_game_id,created_at,updated_at) values (?,?,?,'EXTERNAL',?,cast(? as date),?,now(),now())",
                id, title, title.toLowerCase(java.util.Locale.ROOT), type, release, parent);
        jdbc.update("insert into game_external_refs(id,game_id,provider,external_id) values (?,?,'STEAM',?)", UUID.randomUUID(), id, Integer.toString(Math.abs(title.hashCode())));
        return id;
    }

    @Test void mainGameDetailCountsActiveDlcAndDlcDetailNamesItsParent() throws Exception {
        mvc.perform(get("/api/v1/game/games/" + main)).andExpect(status().isOk())
                .andExpect(jsonPath("$.game.gameId").value(main.toString()))
                .andExpect(jsonPath("$.relation.dlcCount").value(3))
                .andExpect(jsonPath("$.relation.parent").isEmpty());
        mvc.perform(get("/api/v1/game/games/" + newest)).andExpect(status().isOk())
                .andExpect(jsonPath("$.relation.parent.gameId").value(main.toString()))
                .andExpect(jsonPath("$.relation.parent.title").value("Main Game"))
                .andExpect(jsonPath("$.relation.dlcCount").isEmpty());
    }

    @Test void unknownParentIsNeverInventedAndOtherTypesHaveNoRelation() throws Exception {
        mvc.perform(get("/api/v1/game/games/" + orphan)).andExpect(status().isOk())
                .andExpect(jsonPath("$.relation.parent").isEmpty());
        mvc.perform(get("/api/v1/game/games/" + demo)).andExpect(status().isOk())
                .andExpect(jsonPath("$.relation").isEmpty());
        // Eski alanlar aynen sürer (geriye uyumluluk)
        mvc.perform(get("/api/v1/game/games/" + orphan)).andExpect(jsonPath("$.game.gameType").value("DLC"))
                .andExpect(jsonPath("$.metadata.gameId").value(orphan.toString())).andExpect(jsonPath("$.freshness.status").exists());
    }

    @Test void dlcListIsPagedSortedByReleaseDateWithUndatedLastAndExcludesInactive() throws Exception {
        mvc.perform(get("/api/v1/game/games/" + main + "/dlc").param("size", "2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].game.gameId").value(newest.toString()))
                .andExpect(jsonPath("$.items[0].releaseDate").value("2026-05-01"))
                .andExpect(jsonPath("$.items[1].game.gameId").value(older.toString()));
        mvc.perform(get("/api/v1/game/games/" + main + "/dlc").param("size", "2").param("page", "1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].game.gameId").value(undated.toString()))
                .andExpect(jsonPath("$.items[0].releaseDate").isEmpty());
    }

    @Test void dlcListHandlesNonMainGamesUnknownIdsAndInvalidPaging() throws Exception {
        mvc.perform(get("/api/v1/game/games/" + newest + "/dlc")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/api/v1/game/games/" + UUID.randomUUID() + "/dlc")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/game/games/" + main + "/dlc").param("size", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/game/games/" + main + "/dlc").param("size", "51")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/game/games/" + main + "/dlc").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/game/games/not-a-uuid/dlc")).andExpect(status().isBadRequest());
    }

    @Test void dlcNeverMixesIntoTheMainGameCatalogList() throws Exception {
        mvc.perform(get("/api/v1/game/games").param("size", "50")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].game.gameId").value(main.toString()));
    }
}
