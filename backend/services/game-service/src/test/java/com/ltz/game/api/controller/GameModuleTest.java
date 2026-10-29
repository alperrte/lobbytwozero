package com.ltz.game.api.controller;
import com.ltz.game.AuthenticatedApiTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
class GameModuleTest extends AuthenticatedApiTest {
    private static final String TITLE="Original achievement";
    private String root(UUID id) { return "/api/v1/game/admin/games/"+id+"/achievements"; }
    private String publicPath(UUID id,String type) { return "/api/v1/game/games/"+id+"/modules/"+type; }
    private UUID active() { return game("Modular game","INTERNAL","MAIN_GAME","ACTIVE"); }
    private String body(boolean visible,String version) {
        return "{\"title\":\""+TITLE+"\",\"description\":\"Definition only\",\"visible\":"+visible
                +",\"reason\":\"Verified admin definition\""+version+"}";
    }
    @Test void publicModulesDistinguishEmptyFromUnavailableAndPreserveLegacyMetadata() throws Exception {
        UUID id=active();
        send(get(publicPath(id,"ACHIEVEMENTS")),null,null).andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value(id.toString())).andExpect(jsonPath("$.state").value("EMPTY"))
                .andExpect(jsonPath("$.totalElements").value(0));
        for(String type:new String[]{"GUIDES","EASTER_EGGS","EXPERIENCES"})
            send(get(publicPath(id,type)),null,null).andExpect(status().isOk())
                    .andExpect(jsonPath("$.state").value("INTEGRATION_PENDING"))
                    .andExpect(jsonPath("$.totalElements").isEmpty()).andExpect(jsonPath("$.items").isEmpty());
        send(get("/api/v1/game/"+id+"/metadata"),null,null).andExpect(status().isOk())
                .andExpect(jsonPath("$.languageDataState").value("UNKNOWN"));
    }
    @Test void adminUpsertIsVersionedAuditedAndHiddenDefinitionsNeverLeak() throws Exception {
        UUID id=active();
        var created=send(put(root(id)+"/first"),admin(),body(true,"")).andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("ADMIN"));
        long version=Long.parseLong(read(created,"$.version"));
        String achievementId=read(created,"$.id");
        send(put(root(id)+"/first"),admin(),body(false,",\"version\":"+version)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(achievementId));
        send(get(publicPath(id,"ACHIEVEMENTS")),null,null).andExpect(jsonPath("$.items").isEmpty());
        send(get(root(id)),admin(),null).andExpect(jsonPath("$.items[0].visible").value(false));
        send(put(root(id)+"/first"),admin(),body(true,",\"version\":"+version)).andExpect(status().isConflict());
        assertEquals(2,jdbc.queryForObject("select count(*) from game_admin_audits where game_id=?",Integer.class,id));
        assertEquals(0,jdbc.queryForObject("select count(*) from game_admin_audits where details like ?",Integer.class,"%"+TITLE+"%"));
    }
    @Test void pagesAreBoundedSortedAndCountOnlyVisibleDefinitions() throws Exception {
        UUID id=active();
        for(String code:new String[]{"z-last","a-first","hidden"})
            send(put(root(id)+"/"+code),admin(),body(!code.equals("hidden"),"")).andExpect(status().isOk());
        send(get(publicPath(id,"ACHIEVEMENTS")+"?size=1"),null,null)
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.items[0].code").value("a-first"));
        send(get(publicPath(id,"ACHIEVEMENTS")+"?size=1&page=1"),null,null).andExpect(jsonPath("$.items[0].code").value("z-last"));
        for(String query:new String[]{"?size=0","?size=51","?page=-1","?page=10001"})
            send(get(publicPath(id,"ACHIEVEMENTS")+query),null,null).andExpect(status().isBadRequest());
    }
    @Test void adminRoleIsRequiredForBothManagementOperations() throws Exception {
        UUID id=active();
        send(put(root(id)+"/first"),null,body(true,"")).andExpect(status().isUnauthorized());
        send(get(root(id)),null,null).andExpect(status().isUnauthorized());
        for(String roleToken:new String[]{token(UUID.randomUUID(),"PLAYER"),developer(),moderator()}) {
            send(put(root(id)+"/first"),roleToken,body(true,"")).andExpect(status().isForbidden());
            send(get(root(id)),roleToken,null).andExpect(status().isForbidden());
        }
    }
    @Test void invalidAndArchivedCanonicalGamesCannotExposeOrReceiveDefinitions() throws Exception {
        UUID archived=game("Archived","INTERNAL","MAIN_GAME","ARCHIVED");
        for(UUID id:new UUID[]{archived,UUID.randomUUID()}) {
            send(get(publicPath(id,"GUIDES")),null,null).andExpect(status().isNotFound());
            send(put(root(id)+"/first"),admin(),body(true,"")).andExpect(status().isNotFound());
        }
        UUID id=active();
        send(get(publicPath(id,"BOGUS")),null,null).andExpect(status().isBadRequest());
        send(put(root(id)+"/INVALID"),admin(),body(true,"")).andExpect(status().isBadRequest());
        send(put(root(id)+"/first"),admin(),body(true,"").replace(TITLE," ")).andExpect(status().isBadRequest());
        send(put(root(id)+"/first"),admin(),body(true,",\"version\":0")).andExpect(status().isConflict());
        assertEquals(0,jdbc.queryForObject("select count(*) from game_achievement_definitions",Integer.class));
    }
    @Test void largeDefinitionCollectionCannotGrowBeyondGameQuota() throws Exception {
        UUID id=active();
        jdbc.update("insert into game_achievement_definitions(id,game_id,code,title,created_at,updated_at) select gen_random_uuid(),?, 'entry-'||n,'Definition',now(),now() from generate_series(1,1000) n",id);
        send(put(root(id)+"/extra"),admin(),body(true,"")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACHIEVEMENT_LIMIT_REACHED"));
        send(get(publicPath(id,"ACHIEVEMENTS")+"?size=50"),null,null).andExpect(jsonPath("$.items.length()").value(50));
    }
}
