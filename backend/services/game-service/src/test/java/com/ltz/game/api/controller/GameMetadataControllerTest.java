package com.ltz.game.api.controller;
import com.ltz.game.PostgresTestSupport;
import com.ltz.game.domain.entity.Game;
import com.ltz.game.infrastructure.persistence.repository.GameRepository;
import java.util.UUID;
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
class GameMetadataControllerTest extends PostgresTestSupport {
    @Autowired WebApplicationContext context;
    @Autowired GameRepository games;
    MockMvc mvc;
    @BeforeEach void setup() { mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }
    @Test void sparseMetadataIsPublicAndHasSeparateArrays() throws Exception {
        Game game=new Game(); game.setTitle("Test");game.setNormalizedTitle("test");games.saveAndFlush(game);
        mvc.perform(get("/api/v1/game/"+game.getId()+"/metadata")).andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value(game.getId().toString()))
                .andExpect(jsonPath("$.genres").isArray()).andExpect(jsonPath("$.categories").isArray())
                .andExpect(jsonPath("$.tags").isArray()).andExpect(jsonPath("$.platforms").isArray());
    }
    @Test void invalidAndMissingIdsReturnProblemDetails() throws Exception {
        mvc.perform(get("/api/v1/game/invalid/metadata")).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(get("/api/v1/game/"+UUID.randomUUID()+"/metadata")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
    @Test void writesAndUnlistedRoutesAreDenied() throws Exception {
        mvc.perform(post("/api/v1/game/"+UUID.randomUUID()+"/metadata")).andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(get("/api/v1/game/admin")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}
