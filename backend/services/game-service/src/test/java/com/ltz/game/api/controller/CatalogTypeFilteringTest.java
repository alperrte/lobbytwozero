package com.ltz.game.api.controller;

import com.ltz.game.PostgresTestSupport;
import com.ltz.game.domain.entity.*;
import com.ltz.game.domain.enums.*;
import com.ltz.game.infrastructure.persistence.repository.GameRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @Transactional
class CatalogTypeFilteringTest extends PostgresTestSupport {
    @Autowired WebApplicationContext context;
    @Autowired GameRepository games;
    @Test void nonGamesAndUnknownNeverMixIntoDefaultMainGameResults() throws Exception {
        MockMvc mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        int appId=987650000;
        for(GameType type:GameType.values()) {
            Game game=new Game();game.setTitle("Typed fixture "+type);game.setNormalizedTitle("typed fixture "+type.name().toLowerCase(java.util.Locale.ROOT));
            game.setOrigin(GameOrigin.EXTERNAL);game.setGameType(type);
            GameExternalReference ref=new GameExternalReference();ref.setGame(game);ref.setProvider("STEAM");ref.setExternalId(Integer.toString(appId++));
            game.getExternalReferences().add(ref);games.saveAndFlush(game);
        }
        mvc.perform(get("/api/v1/game/catalog").param("q","Typed fixture"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].gameType").value("MAIN_GAME"));
        mvc.perform(get("/api/v1/game/catalog").param("q","Typed fixture").param("includeUnclassified","true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[1].gameType").value("UNKNOWN"));
        mvc.perform(get("/api/v1/game/catalog/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.unclassifiedEntries").isNumber()).andExpect(jsonPath("$.mainGameEntries").isNumber());
    }
}
