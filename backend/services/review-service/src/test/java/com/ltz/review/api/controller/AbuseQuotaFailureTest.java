package com.ltz.review.api.controller;

import com.ltz.review.application.port.GameCatalogPort;
import com.ltz.review.infrastructure.persistence.repository.AbuseQuotaRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static com.ltz.review.api.controller.RatingApiTest.actor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Kota deposu çalışmıyorsa korunan işlem güvenli tarafta reddedilir; iş mantığı çalışmaz. */
@SpringBootTest
class AbuseQuotaFailureTest {
    @Autowired WebApplicationContext context;
    @Autowired JdbcClient jdbc;
    @MockitoBean AbuseQuotaRepository quotas;
    @MockitoBean GameCatalogPort games;

    @Test void quotaStoreFailureRejectsProtectedWritesWithoutRunningBusinessLogic() throws Exception {
        when(quotas.consume(any(), any(), any(), any(), anyInt())).thenThrow(new DataAccessResourceFailureException("db"));
        var mvc = MockMvcBuilders.webAppContextSetup(context).defaultRequest(get("/").with(csrf())).apply(springSecurity()).build();
        UUID user = UUID.randomUUID();
        mvc.perform(post("/api/v1/review/games/" + UUID.randomUUID() + "/reviews").with(actor(user))
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Deneyim\"}")).andExpect(status().isInternalServerError());
        mvc.perform(put("/api/v1/review/reviews/" + UUID.randomUUID() + "/like").with(actor(user))).andExpect(status().isInternalServerError());
        verifyNoInteractions(games);
        assertEquals(0L, jdbc.sql("SELECT count(*) FROM reviews WHERE user_id = ?").param(user).query(Long.class).single());
    }
}
