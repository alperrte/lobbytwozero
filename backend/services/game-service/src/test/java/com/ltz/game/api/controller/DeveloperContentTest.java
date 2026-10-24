package com.ltz.game.api.controller;

import com.ltz.game.AuthenticatedApiTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** GAME-12 (oyun bazlı geliştirici yetkisi), GAME-13 (geliştirici içerikleri) ve moderasyon politikası. */
@Import(AuthenticatedApiTest.Fakes.class)
class DeveloperContentTest extends AuthenticatedApiTest {
    static final String REASON = "Yönetici işlemi için yeterli gerekçe";
    static final String IMAGE = "https://cdn.akamai.steamstatic.com/steam/apps/10/header.jpg";
    static final String VIDEO = "https://www.youtube.com/watch?v=abc123";

    UUID gameWithGrant() throws Exception {
        UUID id = game("Dev Game", "INTERNAL", "MAIN_GAME", "ACTIVE");
        grant(id, DEVELOPER_ID);
        return id;
    }
    void grant(UUID game, UUID developer) throws Exception {
        send(post("/api/v1/game/admin/games/" + game + "/developers"), admin(),
                "{\"developerUserId\":\"" + developer + "\",\"reason\":\"" + REASON + "\"}").andExpect(status().isOk());
    }
    String base(UUID game) { return "/api/v1/game/developer/games/" + game; }
    String media(String type, String url) { return "{\"type\":\"" + type + "\",\"url\":\"" + url + "\"}"; }
    long audits(UUID game, String action) {
        return jdbc.queryForObject("select count(*) from game_admin_audits where game_id=? and action=?", Long.class, game, action);
    }
    String note(UUID game, String title) throws Exception {
        return read(send(post(base(game) + "/update-notes"), developer(), "{\"title\":\"" + title + "\",\"body\":\"Yama notu\"}")
                .andExpect(status().isCreated()), "$.id");
    }

    @Test void grantsAreAdminOnlyIdempotentAuditedAndRevocable() throws Exception {
        UUID id = game("Dev Game", "INTERNAL", "MAIN_GAME", "ACTIVE");
        String path = "/api/v1/game/admin/games/" + id + "/developers";
        String body = "{\"developerUserId\":\"" + DEVELOPER_ID + "\",\"reason\":\"" + REASON + "\"}";
        send(post(path), moderator(), body).andExpect(status().isForbidden());
        send(post(path), developer(), body).andExpect(status().isForbidden());
        for (String invalid : List.of("{\"reason\":\"" + REASON + "\"}", "{\"developerUserId\":\"" + DEVELOPER_ID + "\",\"reason\":\"kısa\"}",
                "{\"developerUserId\":\"nope\",\"reason\":\"" + REASON + "\"}", "{\"developerUserId\":\"" + DEVELOPER_ID + "\",\"reason\":\"" + REASON + "\",\"role\":\"ADMIN\"}"))
            send(post(path), admin(), invalid).andExpect(status().isBadRequest());
        send(post("/api/v1/game/admin/games/" + UUID.randomUUID() + "/developers"), admin(), body).andExpect(status().isNotFound());
        String grantId = read(send(post(path), admin(), body).andExpect(status().isOk()).andExpect(jsonPath("$.developerUserId").value(DEVELOPER_ID.toString())), "$.grantId");
        assertEquals(grantId, read(send(post(path), admin(), body).andExpect(status().isOk()), "$.grantId"));
        assertEquals(1L, jdbc.queryForObject("select count(*) from game_developer_grants where game_id=? and revoked_at is null", Long.class, id));
        assertEquals(1, audits(id, "GRANT_DEVELOPER"));
        send(get(path), admin(), null).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        send(get("/api/v1/game/developer/games"), developer(), null).andExpect(status().isOk()).andExpect(jsonPath("$[0].gameId").value(id.toString()))
                .andExpect(jsonPath("$[0].title").value("Dev Game"));
        send(get("/api/v1/game/developer/games"), otherDeveloper(), null).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());

        String revoke = path + "/" + DEVELOPER_ID + "/revoke";
        send(post(revoke), moderator(), "{\"reason\":\"" + REASON + "\"}").andExpect(status().isForbidden());
        send(post(revoke), admin(), "{\"reason\":\"kısa\"}").andExpect(status().isBadRequest());
        send(post(revoke), admin(), "{\"reason\":\"" + REASON + "\"}").andExpect(status().isNoContent());
        send(post(revoke), admin(), "{\"reason\":\"" + REASON + "\"}").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("GRANT_NOT_FOUND"));
        assertEquals(1, audits(id, "REVOKE_DEVELOPER"));
        send(get("/api/v1/game/developer/games"), developer(), null).andExpect(jsonPath("$").isEmpty());
        send(get(base(id) + "/content"), developer(), null).andExpect(status().isForbidden());
        // Yetki yeniden verilebilir; geçmiş kayıt korunur.
        grant(id, DEVELOPER_ID);
        assertEquals(2L, jdbc.queryForObject("select count(*) from game_developer_grants where game_id=?", Long.class, id));
        send(get(base(id) + "/content"), developer(), null).andExpect(status().isOk());
    }

    @Test void managementRequiresBothTheTokenRoleAndAGameSpecificGrant() throws Exception {
        UUID owned = gameWithGrant();
        UUID other = game("Someone else's", "INTERNAL", "MAIN_GAME", "ACTIVE");
        grant(other, OTHER_DEVELOPER_ID);
        send(get(base(owned) + "/content"), developer(), null).andExpect(status().isOk()).andExpect(jsonPath("$.gameId").value(owned.toString()));
        // Rol var, bu oyun için yetki yok → 403 (IDOR).
        String desc = "{\"description\":\"Başkasının oyunu\"}";
        send(get(base(other) + "/content"), developer(), null).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_FORBIDDEN"));
        send(put(base(other) + "/description"), developer(), desc).andExpect(status().isForbidden());
        send(post(base(other) + "/media"), developer(), media("IMAGE", IMAGE)).andExpect(status().isForbidden());
        send(post(base(other) + "/update-notes"), developer(), "{\"title\":\"x\",\"body\":\"y\"}").andExpect(status().isForbidden());
        send(delete(base(other) + "/media/" + UUID.randomUUID()), developer(), null).andExpect(status().isForbidden());
        // Rol yok, yetki kaydı var → rol kapısında 403; yönetici rolü geliştirici yetkisi sağlamaz.
        send(get(base(owned) + "/content"), token(DEVELOPER_ID, "PLAYER"), null).andExpect(status().isForbidden());
        send(get(base(owned) + "/content"), admin(), null).andExpect(status().isForbidden());
        send(get(base(owned) + "/content"), token(DEVELOPER_ID, "ADMIN", "MODERATOR"), null).andExpect(status().isForbidden());
        // Rol var, yetki kaydı yok.
        send(get(base(owned) + "/content"), otherDeveloper(), null).andExpect(status().isForbidden());
        // Bir oyunun medyası başka bir oyunun yolundan silinemez.
        String mediaOnOther = read(send(post(base(other) + "/media"), otherDeveloper(), media("IMAGE", IMAGE)).andExpect(status().isCreated()), "$.id");
        send(delete(base(owned) + "/media/" + mediaOnOther), developer(), null).andExpect(status().isNotFound());
        assertEquals("VISIBLE", jdbc.queryForObject("select status from game_developer_media where id=?", String.class, UUID.fromString(mediaOnOther)));
        // Arşivlenmiş oyun yönetilemez; kaynak görünmez.
        send(post("/api/v1/game/admin/games/" + owned + "/archive"), admin(), "{\"reason\":\"" + REASON + "\"}").andExpect(status().isOk());
        send(get(base(owned) + "/content"), developer(), null).andExpect(status().isNotFound());
        send(put(base(owned) + "/description"), developer(), desc).andExpect(status().isNotFound());
    }

    @Test void descriptionIsVersionedValidatedAndNotMassAssignable() throws Exception {
        UUID id = gameWithGrant();
        send(put(base(id) + "/description"), developer(), "{\"description\":\"İlk açıklama\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VISIBLE")).andExpect(jsonPath("$.text").value("İlk açıklama"));
        // Var olan açıklamaya sürümsüz yazma kör üzerine yazmadır.
        send(put(base(id) + "/description"), developer(), "{\"description\":\"Ezme\"}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        long version = Long.parseLong(read(send(get(base(id) + "/content"), developer(), null).andExpect(jsonPath("$.description.text").value("İlk açıklama")), "$.description.version"));
        send(put(base(id) + "/description"), developer(), "{\"description\":\"Güncel\",\"version\":" + version + "}").andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(version + 1));
        send(put(base(id) + "/description"), developer(), "{\"description\":\"Eski\",\"version\":" + version + "}").andExpect(status().isConflict());
        // Katalog açıklaması etkilenmez; geliştirici içeriği ayrı tutulur.
        mvc.perform(get("/api/v1/game/games/" + id)).andExpect(jsonPath("$.description").isEmpty());
        mvc.perform(get("/api/v1/game/games/" + id + "/developer-content")).andExpect(jsonPath("$.description.text").value("Güncel"));
        assertEquals(DEVELOPER_ID, jdbc.queryForObject("select author_id from game_developer_contents where game_id=?", UUID.class, id));
        for (String invalid : List.of("{\"description\":\"\"}", "{\"description\":\"   \"}", "{}", "{\"description\":\"" + "d".repeat(8001) + "\"}",
                "{\"description\":\"kontrol\\u0000karakteri\",\"version\":" + (version + 1) + "}",
                "{\"description\":\"x\",\"version\":" + (version + 1) + ",\"authorId\":\"" + UUID.randomUUID() + "\"}",
                "{\"description\":\"x\",\"version\":" + (version + 1) + ",\"status\":\"HIDDEN\"}"))
            send(put(base(id) + "/description"), developer(), invalid).andExpect(status().isBadRequest());
        // Olmayan açıklamaya sürüm vermek de çakışmadır.
        UUID fresh = game("Fresh", "INTERNAL", "MAIN_GAME", "ACTIVE");
        grant(fresh, DEVELOPER_ID);
        send(put(base(fresh) + "/description"), developer(), "{\"description\":\"x\",\"version\":3}").andExpect(status().isConflict());
    }

    @Test void mediaLinksAreValidatedPerTypeLimitedAndSoftDeleted() throws Exception {
        UUID id = gameWithGrant();
        String image = read(send(post(base(id) + "/media"), developer(), "{\"type\":\"IMAGE\",\"url\":\"" + IMAGE + "\",\"altText\":\"Kapak\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("VISIBLE")).andExpect(jsonPath("$.altText").value("Kapak")), "$.id");
        send(post(base(id) + "/media"), developer(), media("VIDEO", VIDEO)).andExpect(status().isCreated());
        send(post(base(id) + "/media"), developer(), media("DEMO", "https://store.steampowered.com/app/10/Example/")).andExpect(status().isCreated());
        for (String invalid : List.of(media("IMAGE", VIDEO), media("VIDEO", IMAGE), media("DEMO", VIDEO),
                media("IMAGE", "http://cdn.akamai.steamstatic.com/a.jpg"), media("IMAGE", "https://evil.example/a.jpg"),
                media("IMAGE", "https://cdn.akamai.steamstatic.com.evil.example/a.jpg"), media("IMAGE", "https://user@cdn.akamai.steamstatic.com/a.jpg"),
                media("IMAGE", "https://cdn.akamai.steamstatic.com:8443/a.jpg"), media("IMAGE", "https://127.0.0.1/a.jpg"),
                media("IMAGE", "javascript:alert(1)"), media("IMAGE", "https://cdn.akamai.steamstatic.com/a b.jpg")))
            send(post(base(id) + "/media"), developer(), invalid).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_MEDIA_URL"));
        for (String invalid : List.of("{\"url\":\"" + IMAGE + "\"}", "{\"type\":\"IMAGE\"}", media("GIF", IMAGE), "{\"type\":null,\"url\":\"" + IMAGE + "\"}",
                "{\"type\":\"IMAGE\",\"url\":\"" + IMAGE + "\",\"altText\":\"" + "a".repeat(201) + "\"}",
                "{\"type\":\"IMAGE\",\"url\":\"" + IMAGE + "\",\"authorId\":\"" + UUID.randomUUID() + "\"}",
                "{\"type\":\"IMAGE\",\"url\":\"" + IMAGE + "\",\"altText\":\"a\\u0007b\"}"))
            send(post(base(id) + "/media"), developer(), invalid).andExpect(status().isBadRequest());
        // Tür başına sınır (DEMO ≤ 3) ve silinen kaydın yer açması.
        send(post(base(id) + "/media"), developer(), media("DEMO", "https://store.steampowered.com/app/11/A/")).andExpect(status().isCreated());
        send(post(base(id) + "/media"), developer(), media("DEMO", "https://store.steampowered.com/app/12/B/")).andExpect(status().isCreated());
        send(post(base(id) + "/media"), developer(), media("DEMO", "https://store.steampowered.com/app/13/C/")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEDIA_LIMIT_REACHED"));
        send(delete(base(id) + "/media/" + image), developer(), null).andExpect(status().isNoContent());
        send(delete(base(id) + "/media/" + image), developer(), null).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
        send(get(base(id) + "/content"), developer(), null).andExpect(jsonPath("$.media.length()").value(4));
        assertEquals("DELETED", jdbc.queryForObject("select status from game_developer_media where id=?", String.class, UUID.fromString(image)));
        mvc.perform(get("/api/v1/game/games/" + id + "/developer-content")).andExpect(status().isOk())
                .andExpect(jsonPath("$.contributor").value("DEVELOPER")).andExpect(jsonPath("$.media.length()").value(4))
                .andExpect(jsonPath("$.media[0].authorId").doesNotExist()).andExpect(jsonPath("$.media[0].status").doesNotExist());
        send(delete(base(id) + "/media/not-a-uuid"), developer(), null).andExpect(status().isBadRequest());
    }

    @Test void updateNotesAreVersionedLimitedAndNewestFirst() throws Exception {
        UUID id = gameWithGrant();
        String older = note(id, "Eski not");
        String newer = note(id, "Yeni not");
        jdbc.update("update game_update_notes set created_at = now() - interval '1 day' where id = ?", UUID.fromString(older));
        mvc.perform(get("/api/v1/game/games/" + id + "/developer-content")).andExpect(jsonPath("$.updateNotes[0].id").value(newer))
                .andExpect(jsonPath("$.updateNotes[1].id").value(older)).andExpect(jsonPath("$.updateNotes[0].authorId").doesNotExist());
        long version = Long.parseLong(read(send(get(base(id) + "/content"), developer(), null), "$.updateNotes[0].version"));
        String path = base(id) + "/update-notes/" + newer;
        send(put(path), developer(), "{\"title\":\"Düzenlendi\",\"body\":\"Yeni gövde\",\"version\":" + version + "}").andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Düzenlendi")).andExpect(jsonPath("$.version").value(version + 1));
        send(put(path), developer(), "{\"title\":\"Eski sürüm\",\"body\":\"x\",\"version\":" + version + "}").andExpect(status().isConflict());
        send(put(path), developer(), "{\"title\":\"Sürümsüz\",\"body\":\"x\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VERSION_REQUIRED"));
        for (String invalid : List.of("{\"title\":\"\",\"body\":\"x\"}", "{\"title\":\"x\",\"body\":\"  \"}", "{\"title\":\"" + "t".repeat(121) + "\",\"body\":\"x\"}",
                "{\"title\":\"x\",\"body\":\"" + "b".repeat(4001) + "\"}", "{\"title\":\"x\"}", "{\"title\":\"x\",\"body\":\"y\",\"status\":\"HIDDEN\"}"))
            send(post(base(id) + "/update-notes"), developer(), invalid).andExpect(status().isBadRequest());
        send(delete(path), developer(), null).andExpect(status().isNoContent());
        send(put(path), developer(), "{\"title\":\"x\",\"body\":\"y\",\"version\":1}").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOTE_NOT_FOUND"));
        send(delete(path), developer(), null).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/game/games/" + id + "/developer-content")).andExpect(jsonPath("$.updateNotes.length()").value(1));
        // Oyun başına en fazla 100 not.
        jdbc.update("insert into game_update_notes(id,game_id,author_id,title,body,status,created_at,updated_at) "
                + "select gen_random_uuid(),?,?,'n','b','VISIBLE',now(),now() from generate_series(1,99) s", id, DEVELOPER_ID);
        send(post(base(id) + "/update-notes"), developer(), "{\"title\":\"101\",\"body\":\"x\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOTE_LIMIT_REACHED"));
    }

    static String hide(String target, UUID targetId, String action) {
        return "{\"target\":\"" + target + "\"" + (targetId == null ? "" : ",\"targetId\":\"" + targetId + "\"")
                + ",\"action\":\"" + action + "\",\"reason\":\"" + REASON + "\"}";
    }

    @Test void moderationValidatesRolesAndTargetsThenHidesContentFromThePublic() throws Exception {
        UUID id = gameWithGrant();
        send(put(base(id) + "/description"), developer(), "{\"description\":\"Açıklama\"}").andExpect(status().isOk());
        UUID media = UUID.fromString(read(send(post(base(id) + "/media"), developer(), media("VIDEO", VIDEO)).andExpect(status().isCreated()), "$.id"));
        UUID noteId = UUID.fromString(note(id, "Yama 1.1"));
        String path = "/api/v1/game/moderation/games/" + id + "/developer-content/actions";
        mvc.perform(get("/api/v1/game/games/" + id + "/developer-content")).andExpect(jsonPath("$.description.text").value("Açıklama"))
                .andExpect(jsonPath("$.media.length()").value(1)).andExpect(jsonPath("$.updateNotes.length()").value(1));

        send(post(path), developer(), hide("DESCRIPTION", null, "HIDE")).andExpect(status().isForbidden());
        for (String invalid : List.of("{\"target\":\"DESCRIPTION\",\"action\":\"HIDE\",\"reason\":\"kısa\"}", hide("DESCRIPTION", media, "HIDE"),
                hide("MEDIA", null, "HIDE"), hide("EVERYTHING", null, "HIDE"), hide("MEDIA", media, "DELETE"),
                "{\"target\":\"MEDIA\",\"targetId\":\"" + media + "\",\"action\":\"HIDE\"}"))
            send(post(path), moderator(), invalid).andExpect(status().isBadRequest());
        send(post(path), moderator(), hide("MEDIA", UUID.randomUUID(), "HIDE")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CONTENT_NOT_FOUND"));
        UUID elsewhere = game("Other", "INTERNAL", "MAIN_GAME", "ACTIVE");
        send(post("/api/v1/game/moderation/games/" + elsewhere + "/developer-content/actions"), moderator(), hide("MEDIA", media, "HIDE")).andExpect(status().isNotFound());

        send(post(path), moderator(), hide("DESCRIPTION", null, "HIDE")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("HIDDEN"));
        send(post(path), moderator(), hide("DESCRIPTION", null, "HIDE")).andExpect(status().isOk());
        send(post(path), admin(), hide("MEDIA", media, "HIDE")).andExpect(status().isOk());
        send(post(path), moderator(), hide("UPDATE_NOTE", noteId, "HIDE")).andExpect(status().isOk());
        assertEquals(3, audits(id, "HIDE_DEVELOPER_CONTENT"));
        assertEquals("MODERATOR", jdbc.queryForObject("select actor_role from game_admin_audits where game_id=? and action='HIDE_DEVELOPER_CONTENT' and details='target=DESCRIPTION'", String.class, id));
        assertEquals("ADMIN", jdbc.queryForObject("select actor_role from game_admin_audits where game_id=? and action='HIDE_DEVELOPER_CONTENT' and details like 'target=MEDIA%'", String.class, id));
        mvc.perform(get("/api/v1/game/games/" + id + "/developer-content")).andExpect(status().isOk())
                .andExpect(jsonPath("$.description").isEmpty()).andExpect(jsonPath("$.media").isEmpty()).andExpect(jsonPath("$.updateNotes").isEmpty());
        // Kataloğun kendisi etkilenmez.
        mvc.perform(get("/api/v1/game/games/" + id)).andExpect(status().isOk());
    }

    @Test void hiddenContentBlocksOwnerEditsUntilRestoredAndOwnerStillSeesTheReason() throws Exception {
        UUID id = gameWithGrant();
        send(put(base(id) + "/description"), developer(), "{\"description\":\"Açıklama\"}").andExpect(status().isOk());
        UUID media = UUID.fromString(read(send(post(base(id) + "/media"), developer(), media("VIDEO", VIDEO)).andExpect(status().isCreated()), "$.id"));
        UUID noteId = UUID.fromString(note(id, "Yama 1.1"));
        String path = "/api/v1/game/moderation/games/" + id + "/developer-content/actions";
        send(post(path), moderator(), hide("DESCRIPTION", null, "HIDE")).andExpect(status().isOk());
        send(post(path), admin(), hide("MEDIA", media, "HIDE")).andExpect(status().isOk());
        send(post(path), moderator(), hide("UPDATE_NOTE", noteId, "HIDE")).andExpect(status().isOk());

        // Sahibi durumu ve gerekçeyi görür; gizlenen içeriği düzenleyemez ama kaldırabilir.
        send(get(base(id) + "/content"), developer(), null).andExpect(jsonPath("$.description.status").value("HIDDEN"))
                .andExpect(jsonPath("$.description.moderationReason").value(REASON)).andExpect(jsonPath("$.media[0].status").value("HIDDEN"));
        long version = Long.parseLong(read(send(get(base(id) + "/content"), developer(), null), "$.description.version"));
        send(put(base(id) + "/description"), developer(), "{\"description\":\"Tekrar\",\"version\":" + version + "}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTENT_HIDDEN"));
        send(put(base(id) + "/update-notes/" + noteId), developer(), "{\"title\":\"x\",\"body\":\"y\",\"version\":0}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTENT_HIDDEN"));

        send(post(path), moderator(), hide("DESCRIPTION", null, "RESTORE")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VISIBLE"));
        assertEquals(1, audits(id, "RESTORE_DEVELOPER_CONTENT"));
        mvc.perform(get("/api/v1/game/games/" + id + "/developer-content")).andExpect(jsonPath("$.description.text").value("Açıklama"));
        send(get(base(id) + "/content"), developer(), null).andExpect(jsonPath("$.description.moderationReason").isEmpty());
        send(delete(base(id) + "/update-notes/" + noteId), developer(), null).andExpect(status().isNoContent());
        send(post(path), moderator(), hide("UPDATE_NOTE", noteId, "RESTORE")).andExpect(status().isNotFound());
    }

    @Test void publicDeveloperContentIsOnlyForActiveGamesAndEmptyByDefault() throws Exception {
        UUID id = game("Plain", "INTERNAL", "MAIN_GAME", "ACTIVE");
        mvc.perform(get("/api/v1/game/games/" + id + "/developer-content")).andExpect(status().isOk()).andExpect(jsonPath("$.contributor").value("DEVELOPER"))
                .andExpect(jsonPath("$.description").isEmpty()).andExpect(jsonPath("$.media").isEmpty()).andExpect(jsonPath("$.updateNotes").isEmpty());
        UUID archived = game("Archived", "INTERNAL", "MAIN_GAME", "ARCHIVED");
        mvc.perform(get("/api/v1/game/games/" + archived + "/developer-content")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/game/games/" + UUID.randomUUID() + "/developer-content")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/game/games/not-a-uuid/developer-content")).andExpect(status().isBadRequest());
    }

    @Test void databaseEnforcesOneActiveGrantPerDeveloperAndGame() {
        UUID id = game("Grants", "INTERNAL", "MAIN_GAME", "ACTIVE");
        String sql = "insert into game_developer_grants(id,game_id,developer_user_id,granted_by,granted_at) values (gen_random_uuid(),?,?,?,now())";
        jdbc.update(sql, id, DEVELOPER_ID, ADMIN_ID);
        assertThrows(DataAccessException.class, () -> jdbc.update(sql, id, DEVELOPER_ID, ADMIN_ID));
    }

    @Test void databaseRejectsInconsistentRevocation() {
        UUID id = game("Constraints", "INTERNAL", "MAIN_GAME", "ACTIVE");
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "insert into game_developer_grants(id,game_id,developer_user_id,granted_by,granted_at,revoked_at) values (gen_random_uuid(),?,?,?,now(),now())",
                id, DEVELOPER_ID, ADMIN_ID));
    }

    @Test void databaseRejectsNonHttpsMediaLinks() {
        UUID id = game("Constraints", "INTERNAL", "MAIN_GAME", "ACTIVE");
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "insert into game_developer_media(id,game_id,author_id,type,url,status,created_at,updated_at) values (gen_random_uuid(),?,?,'IMAGE','http://x.example/a','VISIBLE',now(),now())",
                id, DEVELOPER_ID));
    }
}
