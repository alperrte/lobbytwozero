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
    @Autowired javax.sql.DataSource dataSource;

    @Test
    void initializesAllHardwareTablesWithEightOrderedMigrations() {
        flyway.validate();
        assertThat(Arrays.stream(flyway.info().applied())
                .map(migration -> migration.getVersion().getVersion()))
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8");
        assertThat(jdbc.queryForList("""
                SELECT tablename FROM pg_tables
                WHERE schemaname='public' AND tablename <> 'flyway_schema_history'
                """, String.class)).containsExactlyInAnyOrder(
                "hardware_profiles", "hardware_profile_field_visibility",
                "hardware_profile_category_visibility", "hardware_brands", "hardware_products",
                "hardware_product_brands", "hardware_catalog_audit", "hardware_profile_components",
                "hardware_campaigns", "hardware_reviews", "hardware_review_audit", "hardware_write_limits",
                "outbox_events", "hardware_review_reports", "hardware_sync_consents", "hardware_sync_receipts",
                "hardware_account_erasure_fences");
    }

    @Test
    void repeatedStartupValidatesChecksumsWithoutExecutingAnotherMigration() {
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        flyway.validate();
    }

    @Test
    void upgradesV7WithExistingDataWithoutResettingHistoryOrPersonalAudit() throws Exception {
        String schema="hw_upgrade_"+java.util.UUID.randomUUID().toString().replace("-", "");
        var previous=Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("7").load();
        previous.migrate();
        var owner=java.util.UUID.randomUUID(); var profile=java.util.UUID.randomUUID();
        try (var connection=dataSource.getConnection()) {
            var isolated=new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection,true));
            isolated.execute("SET search_path TO "+schema);
            try {
                isolated.update("""
                    INSERT INTO hardware_profiles(id,user_id,name,device_type,created_at,updated_at)
                    VALUES (?,?,'Existing PC','DESKTOP',now(),now())
                    """,profile,owner);
                isolated.update("""
                    INSERT INTO hardware_catalog_audit(id,actor_id,action,target_id,reason)
                    VALUES (?,?,'CREATE_PRODUCT',?,'Existing immutable audit')
                    """,java.util.UUID.randomUUID(),owner,java.util.UUID.randomUUID());
                var upgrade=Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load();
                assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
                upgrade.validate();
                assertThat(isolated.queryForObject("SELECT count(*) FROM hardware_profiles",Integer.class)).isEqualTo(1);
                assertThat(isolated.queryForObject("SELECT count(*) FROM hardware_catalog_audit",Integer.class)).isEqualTo(1);
                isolated.queryForList("SELECT erase_hardware_account(?)",owner);
                assertThat(isolated.queryForObject("SELECT count(*) FROM hardware_profiles",Integer.class)).isZero();
                assertThat(isolated.queryForObject("SELECT count(*) FROM hardware_catalog_audit",Integer.class)).isZero();
                assertThat(upgrade.migrate().migrationsExecuted).isZero();
            } finally { isolated.execute("SET search_path TO public"); }
        } finally { jdbc.execute("DROP SCHEMA "+schema+" CASCADE"); }
    }
}
