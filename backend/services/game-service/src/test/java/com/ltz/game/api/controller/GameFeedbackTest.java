package com.ltz.game.api.controller;
import com.ltz.game.AuthenticatedApiTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
class GameFeedbackTest extends AuthenticatedApiTest {
    private static final String ROOT="/api/v1/game/feedback";
    private static final String PRIVATE_BODY="Private bug details";
    private final UUID player=UUID.randomUUID();
    @org.springframework.beans.factory.annotation.Autowired jakarta.persistence.EntityManager entityManager;
    private String playerToken() { return token(player,"PLAYER"); }
    private UUID active() { return game("Feedback game","INTERNAL","MAIN_GAME","ACTIVE"); }
    private String dev(UUID id) { return "/api/v1/game/developer/games/"+id+"/feedback"; }
    private String body(UUID game,String type) {
        return "{\"gameId\":\""+game+"\",\"type\":\""+type+"\",\"title\":\"Feedback title\",\"body\":\""+PRIVATE_BODY+"\"}";
    }
    private String create(UUID game,String type) throws Exception {
        return read(send(post(ROOT),playerToken(),body(game,type)).andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control","private, no-store")),"$.id");
    }
    private void grant(UUID game) throws Exception {
        send(post("/api/v1/game/admin/games/"+game+"/developers"),admin(),
                "{\"developerUserId\":\""+DEVELOPER_ID+"\",\"reason\":\"Verified developer access\"}").andExpect(status().isOk());
    }
    private String change(String status,long version) { return "{\"status\":\""+status+"\",\"version\":"+version+"}"; }

    @Test void feedbackDebugStringsDoNotExposePrivateContent() {
        var input=new com.ltz.game.api.dto.request.FeedbackRequest(UUID.randomUUID(),com.ltz.game.domain.enums.FeedbackType.GENERAL,"Sensitive title",PRIVATE_BODY);
        var output=new com.ltz.game.api.dto.response.FeedbackResponse(UUID.randomUUID(),input.gameId(),input.type(),
                com.ltz.game.domain.enums.FeedbackStatus.OPEN,input.title(),input.body(),0,java.time.Instant.EPOCH,java.time.Instant.EPOCH);
        for(String debug:new String[]{input.toString(),output.toString()}) {
            assertFalse(debug.contains(PRIVATE_BODY)); assertFalse(debug.contains(input.title()));
        }
    }

    @Test void authorComesFromVerifiedSubjectAndOwnReadDoesNotDiscloseOtherAuthors() throws Exception {
        UUID game=active(); String id=create(game,"BUG_REPORT");
        assertEquals(player,jdbc.queryForObject("select author_id from game_developer_feedback where id=?",UUID.class,UUID.fromString(id)));
        send(get(ROOT+"/"+id),playerToken(),null).andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value(PRIVATE_BODY)).andExpect(jsonPath("$.authorId").doesNotExist());
        send(get(ROOT+"/"+id),token(UUID.randomUUID(),"PLAYER"),null).andExpect(status().isNotFound());
        send(get(ROOT+"/"+id),admin(),null).andExpect(status().isNotFound());
        send(get(ROOT+"/"+id),moderator(),null).andExpect(status().isNotFound());
        send(get(ROOT),token(UUID.randomUUID(),"PLAYER"),null).andExpect(jsonPath("$.items").isEmpty());
        send(post(ROOT),token(UUID.randomUUID(),"PLAYER"),body(game,"GENERAL").replace("}",",\"userId\":\""+player+"\"}"))
                .andExpect(status().isBadRequest());
    }
    @Test void allPrivatePathsRequireAuthenticationAndNoPublicReadExists() throws Exception {
        UUID game=active(); String id=create(game,"GENERAL");
        for(String path:new String[]{ROOT,ROOT+"/"+id,dev(game),dev(game)+"/"+id})
            send(get(path),null,null).andExpect(status().isUnauthorized());
        send(post(ROOT),null,body(game,"GENERAL")).andExpect(status().isUnauthorized());
        send(patch(dev(game)+"/"+id+"/status"),null,change("IN_PROGRESS",0)).andExpect(status().isUnauthorized());
        send(get("/api/v1/game/games/"+game),null,null).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(PRIVATE_BODY))));
    }
    @Test void roleAndGameGrantAreBothRequiredAndCrossGameIdsAreHidden() throws Exception {
        UUID game=active(),other=active(); String id=create(game,"GENERAL");
        for(String denied:new String[]{playerToken(),developer(),otherDeveloper(),admin(),moderator()})
            send(get(dev(game)),denied,null).andExpect(status().isForbidden());
        grant(game); grant(other);
        send(get(dev(game)),developer(),null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(header().string("Cache-Control","private, no-store"));
        send(get(dev(game)+"/"+id),developer(),null).andExpect(status().isOk());
        send(get(dev(other)+"/"+id),developer(),null).andExpect(status().isNotFound());
        send(patch(dev(other)+"/"+id+"/status"),developer(),change("IN_PROGRESS",0)).andExpect(status().isNotFound());
        send(post("/api/v1/game/admin/games/"+game+"/developers/"+DEVELOPER_ID+"/revoke"),admin(),
                "{\"reason\":\"Verified access revocation\"}").andExpect(status().isNoContent());
        send(get(dev(game)),developer(),null).andExpect(status().isForbidden());
        send(patch(dev(game)+"/"+id+"/status"),developer(),change("IN_PROGRESS",0)).andExpect(status().isForbidden());
    }
    @Test void orderedTransitionsAreVersionedAndAuditedWithoutPrivateText() throws Exception {
        UUID game=active(); grant(game); String id=create(game,"BUG_REPORT"); String path=dev(game)+"/"+id+"/status";
        send(patch(path),developer(),change("RESOLVED",0)).andExpect(status().isConflict());
        for(String denied:new String[]{playerToken(),admin(),moderator(),otherDeveloper()})
            send(patch(path),denied,change("IN_PROGRESS",0)).andExpect(status().isForbidden());
        send(patch(path),developer(),change("IN_PROGRESS",0)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        send(patch(path),developer(),change("RESOLVED",0)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        send(patch(path),developer(),change("OPEN",1)).andExpect(status().isConflict());
        send(patch(path),developer(),change("RESOLVED",1)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED"));
        send(patch(path),developer(),change("IN_PROGRESS",2)).andExpect(status().isConflict());
        assertEquals(2,jdbc.queryForObject("select count(*) from game_admin_audits where details like ?",Integer.class,"feedback="+id+";%"));
        assertEquals(0,jdbc.queryForObject("select count(*) from game_admin_audits where details like ?",Integer.class,"%"+PRIVATE_BODY+"%"));
    }
    @Test void earlyAccessRequiresExplicitTrueAndAllThreeTypesRemainDistinct() throws Exception {
        UUID game=active();
        send(post(ROOT),playerToken(),body(game,"EARLY_ACCESS")).andExpect(status().isBadRequest());
        jdbc.update("update games set early_access=false where id=?",game);
        entityManager.clear();
        send(post(ROOT),playerToken(),body(game,"EARLY_ACCESS")).andExpect(status().isBadRequest());
        jdbc.update("update games set early_access=true where id=?",game);
        entityManager.clear();
        for(String type:new String[]{"GENERAL","BUG_REPORT","EARLY_ACCESS"}) create(game,type);
        send(get(ROOT+"?type=EARLY_ACCESS"),playerToken(),null).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].type").value("EARLY_ACCESS"));
    }
    @Test void archivedAndUnknownGamesRejectSubmissionWhileAuthorRetainsExistingRecord() throws Exception {
        UUID game=active(); String id=create(game,"GENERAL");
        jdbc.update("update games set status='ARCHIVED' where id=?",game);
        entityManager.clear();
        send(post(ROOT),playerToken(),body(game,"GENERAL")).andExpect(status().isNotFound());
        send(post(ROOT),playerToken(),body(UUID.randomUUID(),"GENERAL")).andExpect(status().isNotFound());
        send(get(ROOT+"/"+id),playerToken(),null).andExpect(status().isOk());
    }
    @Test void invalidInputsAndMassAssignmentDoNotCreateData() throws Exception {
        UUID game=active(); String valid=body(game,"GENERAL");
        for(String invalid:new String[]{"{}",valid.replace("GENERAL","OTHER"),valid.replace("Feedback title"," "),
                valid.replace(PRIVATE_BODY,"x".repeat(4001)),valid.replace("}",",\"status\":\"RESOLVED\"}"),
                valid.replace("}",",\"version\":12}"),valid.replace(PRIVATE_BODY,"bad\\u0001text")}) {
            send(post(ROOT),token(UUID.randomUUID(),"PLAYER"),invalid).andExpect(status().isBadRequest());
        }
        assertEquals(0,jdbc.queryForObject("select count(*) from game_developer_feedback",Integer.class));
    }
    @Test void privatePaginationUsesStableTieBreakAndFilters() throws Exception {
        UUID game=active(); grant(game);
        for(int i=0;i<3;i++) create(game,i==0?"BUG_REPORT":"GENERAL");
        jdbc.update("update game_developer_feedback set created_at='2026-01-01T00:00:00Z' where game_id=?",game);
        var first=send(get(ROOT+"?size=1&gameId="+game),playerToken(),null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
        String firstId=read(first,"$.items[0].id");
        String nextId=read(send(get(ROOT+"?size=1&page=1"),playerToken(),null),"$.items[0].id");
        assertNotEquals(firstId,nextId);
        assertEquals(firstId,read(send(get(ROOT+"?size=1"),playerToken(),null),"$.items[0].id"));
        send(get(dev(game)+"?type=BUG_REPORT&status=OPEN"),developer(),null).andExpect(jsonPath("$.totalElements").value(1));
        for(String query:new String[]{"?size=0","?size=51","?page=-1","?page=10001","?type=OTHER","?status=OTHER"}) {
            send(get(ROOT+query),playerToken(),null).andExpect(status().isBadRequest());
            send(get(dev(game)+query),developer(),null).andExpect(status().isBadRequest());
        }
    }
}
