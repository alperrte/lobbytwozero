package com.ltz.hardware.infrastructure.persistence;

import java.util.Arrays;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"logging.level.root=INFO", "debug=false"})
@AutoConfigureMockMvc
class HardwareSchemaMigrationTest extends PostgresIntegrationSupport {
    @Autowired Flyway flyway;
    @Autowired JdbcTemplate jdbc;

    @Test
    void initializesAllHardwareTablesWithSevenOrderedMigrations() {
        flyway.validate();
        assertThat(Arrays.stream(flyway.info().applied())
                .map(migration -> migration.getVersion().getVersion()))
                .containsExactly("1", "2", "3", "4", "5", "6", "7");
        assertThat(jdbc.queryForList("""
                SELECT tablename FROM pg_tables
                WHERE schemaname='public' AND tablename <> 'flyway_schema_history'
                """, String.class)).containsExactlyInAnyOrder(
                "hardware_profiles", "hardware_profile_field_visibility",
                "hardware_profile_category_visibility", "hardware_brands", "hardware_products",
                "hardware_product_brands", "hardware_catalog_audit", "hardware_profile_components",
                "hardware_campaigns", "hardware_reviews", "hardware_review_audit", "hardware_write_limits",
                "outbox_events", "hardware_review_reports", "hardware_sync_consents", "hardware_sync_receipts");
    }

    @Test
    void repeatedStartupValidatesChecksumsWithoutExecutingAnotherMigration() {
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        flyway.validate();
    }
}
