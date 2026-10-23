package com.ltz.hardware.infrastructure.persistence;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** One disposable PostgreSQL instance per test JVM; no application ENV is loaded. */
public abstract class PostgresIntegrationSupport {
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate fixtureJdbc;

    @org.junit.jupiter.api.BeforeEach
    void clearReviewFixtures() {
        fixtureJdbc.update("DELETE FROM hardware_review_reports");
        fixtureJdbc.update("DELETE FROM hardware_reviews");
        fixtureJdbc.update("DELETE FROM outbox_events");
        fixtureJdbc.update("DELETE FROM hardware_write_limits");
    }
    private static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17-alpine");

    static {
        DATABASE.start();
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }
}
