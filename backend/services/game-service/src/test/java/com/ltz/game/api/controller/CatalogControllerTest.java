package com.ltz.game.api.controller;

import com.ltz.game.PostgresTestSupport;
import com.ltz.game.domain.entity.Game;
import com.ltz.game.domain.entity.GameExternalReference;
import com.ltz.game.domain.enums.GameOrigin;
import com.ltz.game.infrastructure.persistence.repository.GameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest @Transactional
class CatalogControllerTest extends PostgresTestSupport {
    @Autowired WebApplicationContext context;
    @Autowired GameRepository games;
    MockMvc mvc;
    @BeforeEach void setup() { mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }
    @Test void indexAndCoverageArePublicLocalReads() throws Exception {
        Game game=new Game();game.setTitle("Controller index");game.setNormalizedTitle("controller index");game.setOrigin(GameOrigin.EXTERNAL);game.setGameType(com.ltz.game.domain.enums.GameType.MAIN_GAME);
        GameExternalReference ref=new GameExternalReference();ref.setGame(game);ref.setProvider("STEAM");ref.setExternalId("987654321");
        game.getExternalReferences().add(ref);games.saveAndFlush(game);
        mvc.perform(get("/api/v1/game/catalog").param("q","CONTROLLER")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].gameId").value(game.getId().toString()))
                .andExpect(jsonPath("$.items[0].origin").value("EXTERNAL")).andExpect(jsonPath("$.providerTotal").isEmpty());
        mvc.perform(get("/api/v1/game/catalog/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("STEAM")).andExpect(jsonPath("$.providerTotal").isEmpty());
    }
    @Test void invalidQueriesAreProblemDetailsAndWritesStayClosed() throws Exception {
        for(String size : new String[]{"0","101","not-a-number"}) {
            mvc.perform(get("/api/v1/game/catalog").param("q","A").param("size",size)).andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        }
        mvc.perform(get("/api/v1/game/catalog").param("q"," ")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/game/catalog")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/game/catalog/import")).andExpect(status().isForbidden());
    }
}
