package com.ltz.game.api.controller;

import com.ltz.game.AuthenticatedApiTest;
import com.ltz.game.application.port.Actor;
import com.ltz.game.application.service.AdminGameIdentityService;
import com.ltz.game.domain.enums.ActorRole;
import com.ltz.game.domain.exception.AccessForbiddenException;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminGameIdentityControllerTest extends AuthenticatedApiTest {
    @Autowired AdminGameIdentityService wrapper;
    private static final String ROOT="/api/v1/game/admin/identity/";

    private UUID source() {
        UUID id=game("Source title", "EXTERNAL", "MAIN_GAME", "ACTIVE");
        jdbc.update("insert into game_external_refs(id,game_id,provider,external_id) values (?,?,'STEAM','12345')", UUID.randomUUID(),id);
        return id;
    }
    private String body(UUID expected, UUID target) {
        return "{\"provider\":\"STEAM\",\"externalId\":\"12345\",\"expectedGameId\":\""+expected+"\","
                +(target==null ? "" : "\"targetGameId\":\""+target+"\",")+"\"evidence\":\"Verified test evidence\"}";
    }
    private long count(String table) { return jdbc.queryForObject("select count(*) from "+table,Long.class); }

    @Test void bothEndpointsRequireAdmin() throws Exception {
        UUID id=source();
        for (String action : new String[]{"reassign","split"}) {
            String json=body(id,action.equals("split")?null:id);
            send(post(ROOT+action),null,json).andExpect(status().isUnauthorized());
            send(post(ROOT+action),token(UUID.randomUUID(),"PLAYER"),json).andExpect(status().isForbidden());
            send(post(ROOT+action),moderator(),json).andExpect(status().isForbidden());
            send(post(ROOT+action),developer(),json).andExpect(status().isForbidden());
        }
    }
    @Test void wrapperAlsoRejectsNonAdminAndMissingActor() {
        UUID id=UUID.randomUUID();
        Actor actor=new Actor(DEVELOPER_ID,Set.of(ActorRole.GAME_DEVELOPER));
        assertThrows(AccessForbiddenException.class,()->wrapper.reassign(actor,"STEAM","12345",id,id,"Verified evidence"));
        assertThrows(AccessForbiddenException.class,()->wrapper.split(actor,"STEAM","12345",id,"Verified evidence"));
        assertThrows(AccessForbiddenException.class,()->wrapper.split(null,"STEAM","12345",id,"Verified evidence"));
    }
    @Test void strictNoOpSucceedsWithoutAuditAndStaleNoOpConflicts() throws Exception {
        UUID id=source(); long audits=count("game_identity_audits");
        send(post(ROOT+"reassign"),admin(),body(id,id)).andExpect(status().isOk()).andExpect(jsonPath("$.gameId").value(id.toString()));
        send(post(ROOT+"reassign"),admin(),body(UUID.randomUUID(),id)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFERENCE_MAPPING_CONFLICT"));
        assertEquals(audits,count("game_identity_audits"));
    }
    @Test void realRemapAndSplitFailClosedWithoutAnyMutation() throws Exception {
        UUID id=source(), target=game("Target title","INTERNAL","MAIN_GAME","ACTIVE");
        long games=count("games"), audits=count("game_identity_audits");
        for (String action : new String[]{"reassign","split"}) {
            send(post(ROOT+action),admin(),body(id,action.equals("split")?null:target))
                    .andExpect(status().isConflict()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.code").value("FOREIGN_REFERENCE_SAFETY_UNVERIFIED"))
                    .andExpect(jsonPath("$.detail").value("İstek mevcut durumla çakışıyor."));
        }
        assertEquals(games,count("games")); assertEquals(audits,count("game_identity_audits"));
        assertEquals(id,jdbc.queryForObject("select game_id from game_external_refs where external_id='12345'",UUID.class));
        assertEquals("Source title",jdbc.queryForObject("select title from games where id=?",String.class,id));
        assertEquals("Target title",jdbc.queryForObject("select title from games where id=?",String.class,target));
    }
    @Test void missingResourcesAndStaleSplitAreDistinguished() throws Exception {
        UUID id=source();
        send(post(ROOT+"reassign"),admin(),body(id,UUID.randomUUID())).andExpect(status().isNotFound());
        send(post(ROOT+"split"),admin(),body(id,null).replace("12345","12346")).andExpect(status().isNotFound());
        send(post(ROOT+"split"),admin(),body(UUID.randomUUID(),null)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFERENCE_MAPPING_CONFLICT"));
    }
    @Test void invalidInputAndBypassFieldsAreRejected() throws Exception {
        UUID id=source(); String valid=body(id,id);
        for (String json : new String[]{"{}",valid.replace(id.toString(),"invalid"),
                valid.replace("12345","012345"),valid.replace("12345","4294967296"),
                valid.replace("STEAM","OTHER"),valid.replace("Verified test evidence","        "),
                valid.replace("Verified test evidence","x".repeat(1001)),valid.replace("Verified test evidence","short"),
                valid.replace("}",",\"force\":true}")}) {
            send(post(ROOT+"reassign"),admin(),json).andExpect(status().isBadRequest());
        }
        send(post(ROOT+"split"),admin(),"{}").andExpect(status().isBadRequest());
    }
}
