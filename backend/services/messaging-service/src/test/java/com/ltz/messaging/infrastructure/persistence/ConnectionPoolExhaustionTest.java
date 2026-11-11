package com.ltz.messaging.infrastructure.persistence;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;

import com.ltz.messaging.IntegrationTestSupport;

/**
 * Bağlantı havuzu tükenince istek uzun süre beklemez ve {@code 500} yerine {@code 503 SERVICE_BUSY} +
 * {@code Retry-After} döner; havuz boşalınca istekler yeniden çalışır.
 */
@TestPropertySource(properties = {"spring.datasource.hikari.connection-timeout=500",
        "spring.datasource.hikari.maximum-pool-size=3"})
class ConnectionPoolExhaustionTest extends IntegrationTestSupport {

    @Autowired
    private DataSource dataSource;

    @Test
    void havuzTukenince503VeRetryAfterDoner() throws Exception {
        UUID user = newUser();
        // Havuzdaki üç bağlantının hepsi tutulur.
        try (Connection _ = dataSource.getConnection();
             Connection _ = dataSource.getConnection();
             Connection _ = dataSource.getConnection()) {
            listConversations(user, "")
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"))
                    .andExpect(jsonPath("$.code").value("SERVICE_BUSY"));
        }

        listConversations(user, "").andExpect(status().isOk());
    }
}
