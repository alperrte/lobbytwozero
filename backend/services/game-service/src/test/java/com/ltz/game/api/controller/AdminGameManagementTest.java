package com.ltz.game.api.controller;

import com.ltz.game.AuthenticatedApiTest;
import com.ltz.game.application.service.CatalogImportService;
import com.ltz.game.application.service.GameDetailRefreshService;
import com.ltz.game.domain.enums.GameStatus;
import com.ltz.game.infrastructure.persistence.repository.GameExternalReferenceRepository;
import com.ltz.game.infrastructure.persistence.repository.GameRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** GAME-07: yetkili oyun yönetimi, arşivleme, manuel düzeltmelerin senkronizasyonda korunması ve denetim kaydı. */
@Import(AuthenticatedApiTest.Fakes.class)
class AdminGameManagementTest extends AuthenticatedApiTest {
    static final String CREATE = """
            {"title":"Admin Game","description":"Local text","releaseDate":"2024-05-01","earlyAccess":false,"gameType":"MAIN_GAME",
             "languages":["tr","en"],"developers":["Studio"],"publishers":["Pub"],"genres":["action"],"categories":["single-player"],
             "tags":["indie"],"platforms":["pc"],"coverUrl":"https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/10/header.jpg"}
            """;
    static final String REASON = "{\"reason\":\"Yönetici işlemi için yeterli gerekçe\"}";
    @Autowired CatalogImportService imports;
    @Autowired GameDetailRefreshService refresh;
    @Autowired GameRepository games;
    @Autowired GameExternalReferenceRepository refs;
    @Autowired jakarta.persistence.EntityManager entityManager;

    String create(String body) throws Exception { return read(send(post("/api/v1/game/admin/games"), admin(), body).andExpect(status().isCreated()), "$.gameId"); }
    long audits(String gameId, String action) {
        return jdbc.queryForObject("select count(*) from game_admin_audits where game_id=? and action=?", Long.class, UUID.fromString(gameId), action);
    }
    UUID externalGame(String title) {
        provider.page(10, title, false);
        imports.synchronizeNextPage();
        UUID id = refs.findByProviderAndExternalId("STEAM", "10").orElseThrow().getGame().getId();
        // Test tek transaction'da çalıştığı için ilişkili koleksiyonlar yeniden yüklenmeli (üretimde istekler ayrı transaction'dır).
        entityManager.flush();
        entityManager.clear();
        return id;
    }
    long version(UUID id) throws Exception { return Long.parseLong(read(send(get("/api/v1/game/admin/games/" + id), admin(), null).andExpect(status().isOk()), "$.version")); }

    @Test void adminCreatesInternalGameThatAppearsInPublicCatalogWithoutClientControlledIdentity() throws Exception {
        var created = send(post("/api/v1/game/admin/games"), admin(), CREATE).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.game.game.origin").value("INTERNAL"))
                .andExpect(jsonPath("$.game.game.metadataCompleteness").value("PARTIAL"))
                .andExpect(jsonPath("$.game.game.coverUrl").value("https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/10/header.jpg"))
                .andExpect(jsonPath("$.game.metadata.genres[0].code").value("action"))
                .andExpect(jsonPath("$.game.metadata.categories[0].code").value("single-player"))
                .andExpect(jsonPath("$.game.metadata.tags[0].code").value("indie"))
                .andExpect(jsonPath("$.game.metadata.platforms[0].code").value("pc"))
                .andExpect(jsonPath("$.game.metadata.languages.length()").value(2))
                .andExpect(jsonPath("$.game.metadata.developers[0]").value("Studio"))
                .andExpect(jsonPath("$.game.description").value("Local text"))
                .andExpect(jsonPath("$.manualOverrides").isEmpty());
        String id = read(created, "$.gameId");
        mvc.perform(get("/api/v1/game/games/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.game.title").value("Admin Game"));
        mvc.perform(get("/api/v1/game/games/search").param("q", "admin game")).andExpect(jsonPath("$.totalElements").value(1));
        assertEquals(1, audits(id, "CREATE_GAME"));
        // Aynı sözlük kodları ikinci oyunda yeni terim üretmez.
        create(CREATE.replace("Admin Game", "Second Game"));
        assertEquals(1L, jdbc.queryForObject("select count(*) from catalog_terms where kind='GENRE' and code='action'", Long.class));
    }

    @Test void createRejectsInvalidAndClientControlledFields() throws Exception {
        List<String> invalid = List.of(
                "{\"gameType\":\"MAIN_GAME\"}", "{\"title\":\"  \",\"gameType\":\"MAIN_GAME\"}", "{\"title\":\"X\"}",
                "{\"title\":\"X\",\"gameType\":\"MAIN_GAME\",\"origin\":\"EXTERNAL\"}",
                "{\"title\":\"X\",\"gameType\":\"MAIN_GAME\",\"status\":\"ARCHIVED\"}",
                "{\"title\":\"X\",\"gameType\":\"MAIN_GAME\",\"gameId\":\"" + UUID.randomUUID() + "\"}",
                "{\"title\":\"X\",\"gameType\":\"NOPE\"}", "{\"title\":\"X\",\"gameType\":\"MAIN_GAME\",\"languages\":[\"TR\"]}",
                "{\"title\":\"X\",\"gameType\":\"MAIN_GAME\",\"genres\":[\"Action Games\"]}",
                "{\"title\":\"X\",\"gameType\":\"MAIN_GAME\",\"genres\":[null]}",
                "{\"title\":\"X\",\"gameType\":\"MAIN_GAME\",\"developers\":[\"\"]}",
                "{\"title\":\"X\",\"gameType\":\"MAIN_GAME\",\"developers\":[\"a\\u0000b\"]}",
                "{\"title\":\"X\",\"gameType\":\"MAIN_GAME\",\"coverUrl\":\"https://evil.example/cover.jpg\"}",
                "{\"title\":\"X\",\"gameType\":\"MAIN_GAME\",\"coverUrl\":\"http://cdn.akamai.steamstatic.com/cover.jpg\"}",
                "{\"title\":\"X\",\"gameType\":\"MAIN_GAME\",\"releaseDate\":\"not-a-date\"}",
                "{\"title\":\"" + "T".repeat(257) + "\",\"gameType\":\"MAIN_GAME\"}",
                "{\"title\":\"X\",\"description\":\"" + "d".repeat(16001) + "\",\"gameType\":\"MAIN_GAME\"}",
                "not json");
        for (String body : invalid) send(post("/api/v1/game/admin/games"), admin(), body).andExpect(status().isBadRequest());
        assertEquals(0L, jdbc.queryForObject("select count(*) from games", Long.class));
        send(post("/api/v1/game/admin/games"), admin(), "{\"title\":\"X\",\"gameType\":\"MAIN_GAME\",\"coverUrl\":\"https://evil.example/c.jpg\"}")
                .andExpect(jsonPath("$.code").value("INVALID_MEDIA_URL")).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test void updateIsPartialVersionedAndAudited() throws Exception {
        String id = create(CREATE);
        long version = version(UUID.fromString(id));
        var updated = send(patch("/api/v1/game/admin/games/" + id), admin(), "{\"version\":" + version + ",\"earlyAccess\":true,\"genres\":[\"rpg\"],\"languages\":[]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.game.metadata.earlyAccess").value(true))
                .andExpect(jsonPath("$.game.metadata.genres[0].code").value("rpg"))
                .andExpect(jsonPath("$.game.metadata.languages").isEmpty())
                .andExpect(jsonPath("$.game.game.title").value("Admin Game"))
                .andExpect(jsonPath("$.game.metadata.developers[0]").value("Studio"));
        long current = Long.parseLong(read(updated, "$.version"));
        assertTrue(current > version);
        // Dönen sürüm gerçek sürümdür: GET aynı değeri verir ve sonraki PATCH hemen kabul edilir.
        assertEquals(current, version(UUID.fromString(id)));
        assertEquals(1, audits(id, "UPDATE_GAME"));
        assertTrue(jdbc.queryForObject("select details from game_admin_audits where game_id=? and action='UPDATE_GAME'", String.class, UUID.fromString(id)).contains("genres"));
        // Aynı içerikle tekrar: değişiklik yok, sürüm ve denetim sayısı artmaz.
        send(patch("/api/v1/game/admin/games/" + id), admin(), "{\"version\":" + current + ",\"earlyAccess\":true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(current));
        assertEquals(1, audits(id, "UPDATE_GAME"));
        // Eski sürüm çakışmadır, sürümsüz istek geçersizdir, başlık değişikliği normalize başlığı günceller.
        send(patch("/api/v1/game/admin/games/" + id), admin(), "{\"version\":" + version + ",\"title\":\"Stale\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        send(patch("/api/v1/game/admin/games/" + id), admin(), "{\"title\":\"No version\"}").andExpect(status().isBadRequest());
        send(patch("/api/v1/game/admin/games/" + id), admin(), "{\"version\":" + current + ",\"title\":\"  \"}").andExpect(status().isBadRequest());
        send(patch("/api/v1/game/admin/games/" + id), admin(), "{\"version\":" + current + ",\"title\":\"Renamed Title\",\"coverUrl\":\"\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.game.game.coverUrl").isEmpty());
        mvc.perform(get("/api/v1/game/games/search").param("q", "renamed")).andExpect(jsonPath("$.totalElements").value(1));
        // INTERNAL oyunda sağlayıcı kilidi üretilmez.
        assertEquals(0L, jdbc.queryForObject("select count(*) from game_field_overrides", Long.class));
        send(patch("/api/v1/game/admin/games/" + UUID.randomUUID()), admin(), "{\"version\":0}").andExpect(status().isNotFound());
        send(get("/api/v1/game/admin/games/not-a-uuid"), admin(), null).andExpect(status().isBadRequest());
    }

    @Test void manualCorrectionsOnExternalGamesSurviveCatalogSyncUntilReleased() throws Exception {
        UUID id = externalGame("Steam Title");
        long version = version(id);
        send(patch("/api/v1/game/admin/games/" + id), admin(), "{\"version\":" + version + ",\"title\":\"Corrected Title\",\"gameType\":\"DEMO\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.manualOverrides.length()").value(2))
                .andExpect(jsonPath("$.manualOverrides[?(@.field=='TITLE')]").exists())
                .andExpect(jsonPath("$.manualOverrides[?(@.field=='GAME_TYPE')]").exists());
        // Düzeltilmemiş alan kilitlenmez.
        send(patch("/api/v1/game/admin/games/" + id), admin(), "{\"version\":" + version(id) + ",\"earlyAccess\":true}")
                .andExpect(jsonPath("$.manualOverrides.length()").value(2));
        provider.page(10, "Steam Renamed", false);
        imports.startInitial();
        imports.importNextPage(1000);
        var game = games.findById(id).orElseThrow();
        assertEquals("Corrected Title", game.getTitle());
        assertEquals("corrected title", game.getNormalizedTitle());
        assertEquals(com.ltz.game.domain.enums.GameType.DEMO, game.getGameType());
        // Kilit kaldırılınca sonraki senkronizasyon sağlayıcı başlığını yeniden yazar; işlem gerekçeyle denetlenir.
        send(post("/api/v1/game/admin/games/" + id + "/manual-overrides/TITLE/release"), admin(), REASON)
                .andExpect(status().isOk()).andExpect(jsonPath("$.manualOverrides.length()").value(1));
        assertEquals(1, audits(id.toString(), "RELEASE_OVERRIDE"));
        provider.page(10, "Steam Renamed Again", false);
        imports.startInitial();
        imports.importNextPage(1000);
        assertEquals("Steam Renamed Again", games.findById(id).orElseThrow().getTitle());
        assertEquals(com.ltz.game.domain.enums.GameType.DEMO, games.findById(id).orElseThrow().getGameType());
        send(post("/api/v1/game/admin/games/" + id + "/manual-overrides/TITLE/release"), admin(), REASON)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("OVERRIDE_NOT_FOUND"));
        send(post("/api/v1/game/admin/games/" + id + "/manual-overrides/UNKNOWN/release"), admin(), REASON).andExpect(status().isBadRequest());
        send(post("/api/v1/game/admin/games/" + id + "/manual-overrides/TITLE/release"), admin(), "{\"reason\":\"kısa\"}").andExpect(status().isBadRequest());
    }

    @Test void providerDetailRefreshDoesNotOverwriteManuallyCorrectedFields() throws Exception {
        UUID id = externalGame("Detail Game");
        send(patch("/api/v1/game/admin/games/" + id), admin(), "{\"version\":" + version(id) + ",\"description\":\"Admin text\",\"developers\":[\"Custom Dev\"]}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.manualOverrides.length()").value(2));
        provider.detail("Steam description", LocalDate.of(2020, 1, 1));
        assertEquals("REFRESHED", refresh.refresh(id));
        var game = games.findById(id).orElseThrow();
        assertEquals("Admin text", game.getDescription());
        assertEquals(LocalDate.of(2020, 1, 1), game.getReleaseDate());
        assertEquals(java.util.Set.of("Custom Dev"), game.getDevelopers());
        assertEquals(java.util.Set.of("Provider Publisher"), game.getPublishers());
        // Kilit kalkınca sağlayıcı verisi geri yazılabilir.
        send(post("/api/v1/game/admin/games/" + id + "/manual-overrides/DESCRIPTION/release"), admin(), REASON).andExpect(status().isOk());
        game.setDetailsExpiresAt(Instant.EPOCH);
        provider.detail("Steam description", LocalDate.of(2021, 1, 1));
        assertEquals("REFRESHED", refresh.refresh(id));
        assertEquals("Steam description", games.findById(id).orElseThrow().getDescription());
        assertEquals(java.util.Set.of("Custom Dev"), games.findById(id).orElseThrow().getDevelopers());
    }

    @Test void archiveHidesGameEverywherePreservesDataIsIdempotentAndRestorable() throws Exception {
        String id = create(CREATE);
        send(post("/api/v1/game/admin/games/" + id + "/archive"), admin(), "{\"reason\":\"\"}").andExpect(status().isBadRequest());
        send(post("/api/v1/game/admin/games/" + id + "/archive"), admin(), "{\"reason\":\"kısa\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
        send(post("/api/v1/game/admin/games/" + id + "/archive"), admin(), "{}").andExpect(status().isBadRequest());
        assertEquals(0, audits(id, "ARCHIVE_GAME"));
        send(post("/api/v1/game/admin/games/" + id + "/archive"), admin(), REASON).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
        send(post("/api/v1/game/admin/games/" + id + "/archive"), admin(), REASON).andExpect(status().isOk());
        assertEquals(1, audits(id, "ARCHIVE_GAME"));
        mvc.perform(get("/api/v1/game/games/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/game/" + id + "/card")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/game/games")).andExpect(jsonPath("$.totalElements").value(0));
        // Veri fiziksel olarak silinmez: yönetim görünümü ve alt veriler korunur.
        send(get("/api/v1/game/admin/games/" + id), admin(), null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.game.metadata.genres[0].code").value("action"));
        assertEquals(1L, jdbc.queryForObject("select count(*) from games where id=?", Long.class, UUID.fromString(id)));
        send(post("/api/v1/game/admin/games/" + id + "/restore"), admin(), REASON).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        send(post("/api/v1/game/admin/games/" + id + "/restore"), admin(), REASON).andExpect(status().isOk());
        assertEquals(1, audits(id, "RESTORE_GAME"));
        mvc.perform(get("/api/v1/game/games/" + id)).andExpect(status().isOk());
        // MERGED kimlik işlemidir; yönetim ucu bunu değiştiremez.
        UUID merged = game("Merged", "INTERNAL", "MAIN_GAME", "MERGED");
        send(post("/api/v1/game/admin/games/" + merged + "/archive"), admin(), REASON).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GAME_MERGED"));
        send(post("/api/v1/game/admin/games/" + UUID.randomUUID() + "/archive"), admin(), REASON).andExpect(status().isNotFound());
    }

    @Test void editorialSelectionIsAdminOnlyValidatedAuditedAndClearedOnArchive() throws Exception {
        String first = create(CREATE);
        String second = create(CREATE.replace("Admin Game", "Second Game"));
        UUID dlc = game("A DLC", "INTERNAL", "DLC", "ACTIVE");
        String body = "{\"gameIds\":[\"" + second + "\",\"" + first + "\"],\"reason\":\"Haftalık editör seçimi\"}";
        send(put("/api/v1/game/admin/popular"), moderator(), body).andExpect(status().isForbidden());
        send(put("/api/v1/game/admin/popular"), admin(), body).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/game/games/popular")).andExpect(jsonPath("$.items[0].game.gameId").value(second))
                .andExpect(jsonPath("$.items[1].game.gameId").value(first)).andExpect(jsonPath("$.popularityBasis").value("EDITORIAL"));
        assertEquals(ADMIN_ID, jdbc.queryForObject("select editor_actor_id from games where id=?", UUID.class, UUID.fromString(first)));
        assertEquals(1L, jdbc.queryForObject("select count(*) from game_admin_audits where action='SET_EDITORIAL' and actor_id=?", Long.class, ADMIN_ID));
        for (String invalid : List.of(
                "{\"gameIds\":[\"" + dlc + "\"],\"reason\":\"Haftalık editör seçimi\"}",
                "{\"gameIds\":[\"" + first + "\",\"" + first + "\"],\"reason\":\"Haftalık editör seçimi\"}",
                "{\"gameIds\":[\"" + UUID.randomUUID() + "\"],\"reason\":\"Haftalık editör seçimi\"}",
                "{\"gameIds\":[\"" + first + "\"],\"reason\":\"kısa\"}", "{\"gameIds\":null,\"reason\":\"Haftalık editör seçimi\"}"))
            send(put("/api/v1/game/admin/popular"), admin(), invalid).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/game/games/popular")).andExpect(jsonPath("$.items.length()").value(2));
        send(post("/api/v1/game/admin/games/" + second + "/archive"), admin(), REASON).andExpect(status().isOk());
        assertNull(jdbc.queryForObject("select editor_rank from games where id=?", Integer.class, UUID.fromString(second)));
        send(put("/api/v1/game/admin/popular"), admin(), "{\"gameIds\":[],\"reason\":\"Seçimi temizle\"}").andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/game/games/popular")).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test void adminAuditRowsAreAppendOnlyUpdate() throws Exception {
        UUID id = UUID.fromString(create(CREATE));
        assertThrows(DataAccessException.class, () -> jdbc.update("update game_admin_audits set reason='tampered reason' where game_id=?", id));
    }

    @Test void adminAuditRowsAreAppendOnlyDelete() throws Exception {
        UUID id = UUID.fromString(create(CREATE));
        assertThrows(DataAccessException.class, () -> jdbc.update("delete from game_admin_audits where game_id=?", id));
    }

    @Test void archivedGameStatusIsStoredAsArchivedNotDeleted() throws Exception {
        String id = create(CREATE);
        send(post("/api/v1/game/admin/games/" + id + "/archive"), admin(), REASON).andExpect(status().isOk());
        assertEquals(GameStatus.ARCHIVED, games.findById(UUID.fromString(id)).orElseThrow().getStatus());
    }
}
