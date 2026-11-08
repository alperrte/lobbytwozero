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
    void initializesAllHardwareTablesWithElevenOrderedMigrations() {
        flyway.validate();
        assertThat(Arrays.stream(flyway.info().applied())
                .map(migration -> migration.getVersion().getVersion()))
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "191", "192", "193");
        assertThat(jdbc.queryForList("""
                SELECT tablename FROM pg_tables
                WHERE schemaname='public' AND tablename <> 'flyway_schema_history'
                """, String.class)).containsExactlyInAnyOrder(
                "hardware_profiles", "hardware_profile_field_visibility",
                "hardware_profile_category_visibility", "hardware_brands", "hardware_products",
                "hardware_product_brands", "hardware_catalog_audit", "hardware_profile_components",
                "hardware_campaigns", "hardware_reviews", "hardware_review_audit", "hardware_write_limits",
                "outbox_events", "hardware_review_reports", "hardware_sync_consents", "hardware_sync_receipts",
                "hardware_account_erasure_fences", "hardware_product_sources", "hardware_product_media",
                "hardware_catalog_import_runs", "hardware_catalog_sync_state", "hardware_dataset_import_runs");
    }

    @Test
    void repeatedStartupValidatesChecksumsWithoutExecutingAnotherMigration() {
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        flyway.validate();
    }

    @Test
    void upgradesV10SourcesPreservingExistingProductUuidAndTechnicalContent() throws Exception {
        String schema = "hw_gpu_" + java.util.UUID.randomUUID().toString().replace("-", "");
        var previous = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("10").load();
        previous.migrate();
        var product = java.util.UUID.randomUUID();
        try (var connection = dataSource.getConnection()) {
            var isolated = new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection, true));
            isolated.execute("SET search_path TO " + schema);
            try {
                isolated.update("INSERT INTO hardware_products(id,category,model,normalized_model) VALUES (?,'CPU','Existing','existing')", product);
                isolated.update("""
                    INSERT INTO hardware_product_sources(id,product_id,source_provider,external_product_id,category,brand,
                        locale,synced_at,quality,content_hash,typed_specs)
                    VALUES (?,?,'PC_PART_DATASET','existing','CPU','AMD','und',now(),'VERIFIED','existing','{}')
                    """, java.util.UUID.randomUUID(), product);
                var before = isolated.queryForList("SELECT to_jsonb(s)::text FROM hardware_product_sources s", String.class);
                var upgrade = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load();
                assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(3); // V191, V192, V193
                upgrade.validate();
                assertThat(isolated.queryForList("SELECT to_jsonb(s)::text FROM hardware_product_sources s", String.class)).isEqualTo(before);
                assertThat(isolated.queryForObject("SELECT id FROM hardware_products", java.util.UUID.class)).isEqualTo(product);
                isolated.update("UPDATE hardware_product_sources SET category='GPU'");
                assertThat(isolated.queryForObject("SELECT category FROM hardware_product_sources", String.class)).isEqualTo("GPU");
            } finally { isolated.execute("SET search_path TO public"); }
        } finally { jdbc.execute("DROP SCHEMA " + schema + " CASCADE"); }
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
                assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(6); // V8-V10, V191-V193
                upgrade.validate();
                assertThat(isolated.queryForObject("SELECT count(*) FROM hardware_profiles",Integer.class)).isEqualTo(1);
                assertThat(isolated.queryForObject("SELECT case_color FROM hardware_profiles",String.class)).isEqualTo("BLACK");
                assertThat(isolated.queryForObject("SELECT count(*) FROM hardware_catalog_audit",Integer.class)).isEqualTo(1);
                isolated.queryForList("SELECT erase_hardware_account(?)",owner);
                assertThat(isolated.queryForObject("SELECT count(*) FROM hardware_profiles",Integer.class)).isZero();
                assertThat(isolated.queryForObject("SELECT count(*) FROM hardware_catalog_audit",Integer.class)).isZero();
                assertThat(upgrade.migrate().migrationsExecuted).isZero();
            } finally { isolated.execute("SET search_path TO public"); }
        } finally { jdbc.execute("DROP SCHEMA "+schema+" CASCADE"); }
    }
}
