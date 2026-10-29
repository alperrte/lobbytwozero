package com.ltz.game.api.controller;

import com.ltz.game.PostgresTestSupport;
import com.ltz.game.application.service.GameViewService;
import com.ltz.game.domain.entity.Game;
import com.ltz.game.domain.entity.GameExternalReference;
import com.ltz.game.domain.entity.GameMedia;
import com.ltz.game.domain.enums.GameOrigin;
import com.ltz.game.domain.enums.GameStatus;
import com.ltz.game.domain.enums.MediaType;
import com.ltz.game.domain.enums.MetadataCompleteness;
import com.ltz.game.infrastructure.persistence.repository.GameRepository;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest @Transactional
class GameViewControllerTest extends PostgresTestSupport {
    @Autowired WebApplicationContext context;
    @Autowired GameRepository games;
    @Autowired GameViewService views;
    @Autowired EntityManager em;
    MockMvc mvc;
    @BeforeEach void setup() { mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }
    @Test void internalAndExternalGamesUseSameContractAndKeepTheirOrigin() throws Exception {
        for (GameOrigin origin : GameOrigin.values()) {
            Game game = game(origin);
            if (origin == GameOrigin.EXTERNAL) {
                GameExternalReference ref=new GameExternalReference();ref.setGame(game);ref.setProvider("STEAM");ref.setExternalId("10");
                game.getExternalReferences().add(ref);
            }
            UUID id=games.saveAndFlush(game).getId();em.clear();
            mvc.perform(get("/api/v1/game/"+id+"/card")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.gameId").value(id.toString())).andExpect(jsonPath("$.origin").value(origin.name()))
                    .andExpect(jsonPath("$.metadataCompleteness").value("INDEX_ONLY"))
                    .andExpect(jsonPath("$.externalReferences").isArray()).andExpect(jsonPath("$.coverUrl").isEmpty());
            mvc.perform(get("/api/v1/game/games/"+id)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.game.gameId").value(id.toString()))
                    .andExpect(jsonPath("$.metadata.gameId").value(id.toString()));
            assertEquals(origin,games.findById(id).orElseThrow().getOrigin());
        }
    }
    @Test void sparseExternalDataAndCoverSelection() {
        Game game=game(GameOrigin.EXTERNAL);game.setMetadataCompleteness(MetadataCompleteness.PARTIAL);
        for(int order : new int[]{2,0}) {
            GameMedia media=new GameMedia();media.setGame(game);media.setType(MediaType.COVER);
            media.setUrl("https://example.com/"+order+".png");media.setSortOrder(order);game.getMedia().add(media);
        }
        UUID id=games.saveAndFlush(game).getId();em.clear();
        var card=views.card(id);assertEquals("https://example.com/0.png",card.coverUrl());
        assertEquals(MetadataCompleteness.PARTIAL,card.metadataCompleteness());
        assertTrue(views.detail(id).metadata().systemRequirements().isEmpty());
    }
    @Test void archivedAndMissingGamesAreNotPublic() throws Exception {
        Game game=game(GameOrigin.INTERNAL);game.setStatus(GameStatus.ARCHIVED);UUID id=games.saveAndFlush(game).getId();
        for(String route : new String[]{"/api/v1/game/"+id+"/card", "/api/v1/game/games/"+id, "/api/v1/game/"+id+"/metadata"}) {
            mvc.perform(get(route)).andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        }
        mvc.perform(get("/api/v1/game/games/"+UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/game/games/invalid")).andExpect(status().isBadRequest());
    }
    private Game game(GameOrigin origin) { Game g=new Game();g.setTitle("Same title");g.setNormalizedTitle("same title");g.setOrigin(origin);return g; }
}
