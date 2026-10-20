package com.ltz.game;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
/** Yalnız geçici test veritabanı; geliştiricinin DB/ENV dosyalarını kullanmaz. */
public abstract class PostgresTestSupport {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    static { POSTGRES.start(); }
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
