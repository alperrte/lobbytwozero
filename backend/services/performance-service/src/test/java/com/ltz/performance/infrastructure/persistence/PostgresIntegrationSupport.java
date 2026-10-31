package com.ltz.performance.infrastructure.persistence;

import com.ltz.performance.infrastructure.client.PerformancePeerDoubles;
import com.ltz.performance.infrastructure.security.PerformanceTestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Test JVM'i başına tek geçici PostgreSQL; uygulama ENV'i yüklenmez. Auth adresi yerel sapmayı gösterir. */
public abstract class PostgresIntegrationSupport {
    private static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17-alpine");

    static {
        DATABASE.start();
    }

    @Autowired
    private JdbcTemplate fixtureJdbc;

    @BeforeEach
    void clearFixtures() {
        fixtureJdbc.update("DELETE FROM performance_reports");
        fixtureJdbc.update("DELETE FROM performance_sessions");
        fixtureJdbc.update("DELETE FROM sync_batches");
        fixtureJdbc.update("DELETE FROM device_links");
        fixtureJdbc.update("DELETE FROM sync_consents");
        fixtureJdbc.update("DELETE FROM performance_write_limits");
        fixtureJdbc.update("DELETE FROM performance_account_erasure_fences");
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("ltz.performance.auth.service-url", PerformanceTestAuth::serviceUrl);
        // Game/Hardware yerel sapmaları (mock); gerçek servis entegrasyonu değildir.
        registry.add("ltz.performance.clients.game-service-url", PerformancePeerDoubles::origin);
        registry.add("ltz.performance.clients.hardware-service-url", PerformancePeerDoubles::origin);
        registry.add("ltz.performance.clients.read-timeout", () -> "1s");
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }
}
