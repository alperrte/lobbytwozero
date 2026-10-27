package com.ltz.game.infrastructure.persistence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Yalnız izole test provası; DEV bağlantısı/operasyon komutu değildir. */
final class MigrationTransferSupport {
    static final UUID GAME = UUID.fromString("10000000-0000-0000-0000-000000000001");
    static final String HISTORY = "classpath:db/history/game-service-v173";
    static final String CURRENT = "classpath:db/migration";
    // FK bağımlılık sırası; audit'ler yalnız INSERT ile aktarılır.
    static final List<String> TABLES = List.of("games", "catalog_terms", "game_terms", "game_developers",
            "game_publishers", "game_media", "game_system_requirements", "game_languages", "game_external_refs",
            "game_provider_sync_state", "game_identity_audits", "game_field_overrides", "game_admin_audits",
            "game_developer_grants", "game_developer_contents", "game_developer_media", "game_update_notes",
            "game_language_supports", "game_metadata_provenance", "game_achievement_definitions", "game_developer_feedback");

    private MigrationTransferSupport() { }

    record TableDigest(long rows, String sha256) { }
    record Receipt(Map<String, TableDigest> tables) {
        Receipt { tables = Map.copyOf(tables); }
    }
    @FunctionalInterface interface Checkpoint { void afterTable(Connection target, int copiedTables) throws SQLException; }

    static DriverManagerDataSource database(PostgreSQLContainer postgres, String chain) {
        String name = "transfer_" + UUID.randomUUID().toString().replace("-", "");
        var admin = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        new JdbcTemplate(admin).execute("create database " + name);
        String url = postgres.getJdbcUrl();
        String base = url.substring(0, url.lastIndexOf('/'));
        String options = url.contains("?") ? url.substring(url.indexOf('?')) : "";
        var result = new DriverManagerDataSource(base + "/" + name + options, postgres.getUsername(), postgres.getPassword());
        flyway(result, chain).migrate();
        return result;
    }

    static Flyway flyway(DataSource source, String location) {
        var configuration = Flyway.configure().dataSource(source).locations(location);
        // Tarihsel V31–V40 yeniden numaralandırma/taşıma provası bu sınırda kalır.
        if (CURRENT.equals(location)) configuration.target("40");
        return configuration.load();
    }

    static void populate(DataSource source) {
        new ResourceDatabasePopulator(new ClassPathResource("db/rehearsal/synthetic_game_data.sql")).execute(source);
    }

    static Receipt transfer(DriverManagerDataSource source, DriverManagerDataSource target,
                            Checkpoint checkpoint) throws Exception {
        if (!MigrationSchemaSupport.schema(source).equals(MigrationSchemaSupport.schema(target))) {
            throw new IllegalStateException("SCHEMA_MISMATCH");
        }
        try (Connection from = source.getConnection(); Connection to = target.getConnection()) {
            from.setReadOnly(true);
            from.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            from.setAutoCommit(false);
            to.setAutoCommit(false);
            try {
                setUtc(from);
                setUtc(to);
                // Hedef yalnız yeni zincir + tek başlangıç satırı olmalı; başka veri ezilmez.
                requireFreshTarget(to);
                int copied = 0;
                for (String table : TABLES) {
                    copyTable(from, to, table);
                    checkpoint.afterTable(to, ++copied);
                }
                requireEqual(from, to);
                Map<String, TableDigest> digest = digests(to);
                to.commit(); // Deferred constraint'ler de burada doğrulanır.
                from.commit();
                return new Receipt(digest); // Başarı makbuzu yalnız commit sonrasında üretilir.
            } catch (Exception failure) {
                to.rollback();
                from.rollback();
                throw failure;
            }
        }
    }

    static void requireReady(DataSource source, DataSource target, Receipt receipt) throws Exception {
        if (receipt == null) throw new IllegalStateException("TRANSFER_NOT_COMMITTED");
        try (Connection from = source.getConnection(); Connection to = target.getConnection()) {
            setUtc(from);
            setUtc(to);
            requireEqual(from, to);
            if (!receipt.tables().equals(digests(to))) throw new IllegalStateException("RECEIPT_MISMATCH");
        }
        Flyway flyway = flyway(target, CURRENT);
        flyway.validate();
        if (flyway.info().pending().length != 0) throw new IllegalStateException("PENDING_MIGRATIONS");
    }

    private static void requireFreshTarget(Connection target) throws SQLException {
        for (String table : TABLES) {
            try (var statement = target.createStatement(); var rows = statement.executeQuery("select count(*) from " + quoted(table))) {
                rows.next();
                long expected = table.equals("game_provider_sync_state") ? 1 : 0;
                if (rows.getLong(1) != expected) throw new IllegalStateException("TARGET_NOT_FRESH: " + table);
            }
        }
        try (var statement = target.createStatement(); var rows = statement.executeQuery(
                "select count(*) from game_provider_sync_state where id='00000000-0000-0000-0000-000000000001' and provider='STEAM'")) {
            rows.next();
            if (rows.getInt(1) != 1) throw new IllegalStateException("INVALID_TARGET_SEED");
        }
    }

    private static void copyTable(Connection source, Connection target, String table) throws SQLException {
        try (var read = source.createStatement(); var rows = read.executeQuery("select * from " + quoted(table))) {
            var metadata = rows.getMetaData();
            List<String> columns = new ArrayList<>();
            for (int column = 1; column <= metadata.getColumnCount(); column++) columns.add(quoted(metadata.getColumnName(column)));
            String sql = "insert into " + quoted(table) + " (" + String.join(",", columns) + ") values ("
                    + String.join(",", java.util.Collections.nCopies(columns.size(), "?")) + ")";
            if (table.equals("game_provider_sync_state")) {
                List<String> updates = columns.stream().filter(c -> !c.equals("\"id\""))
                        .map(c -> c + "=excluded." + c).toList();
                sql += " on conflict (id) do update set " + String.join(",", updates);
            }
            try (var insert = target.prepareStatement(sql)) {
                while (rows.next()) {
                    for (int column = 1; column <= columns.size(); column++) insert.setObject(column, rows.getObject(column));
                    insert.executeUpdate();
                }
            }
        }
    }

    private static void requireEqual(Connection source, Connection target) throws Exception {
        if (!digests(source).equals(digests(target))) throw new IllegalStateException("HASH_MISMATCH");
        for (String table : TABLES) {
            if (!rows(source, table).equals(rows(target, table))) throw new IllegalStateException("DATA_MISMATCH: " + table);
        }
    }

    static Map<String, TableDigest> digests(DataSource source) throws Exception {
        try (Connection connection = source.getConnection()) {
            setUtc(connection);
            return digests(connection);
        }
    }

    private static Map<String, TableDigest> digests(Connection connection) throws Exception {
        Map<String, TableDigest> result = new LinkedHashMap<>();
        for (String table : TABLES) {
            List<String> values = rows(connection, table);
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                hash.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());
                hash.update(bytes);
            }
            result.put(table, new TableDigest(values.size(), HexFormat.of().formatHex(hash.digest())));
        }
        return result;
    }

    private static List<String> rows(Connection connection, String table) throws SQLException {
        List<String> values = new ArrayList<>();
        try (var statement = connection.createStatement(); var result = statement.executeQuery(
                "select row_to_json(t)::text from " + quoted(table) + " t order by row_to_json(t)::text collate \"C\"")) {
            while (result.next()) values.add(result.getString(1));
        }
        return values;
    }

    private static String quoted(String identifier) {
        if (!identifier.matches("[a-z_]+")) throw new IllegalArgumentException("Invalid test identifier");
        return "\"" + identifier + "\"";
    }

    private static void setUtc(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute("set time zone 'UTC'"); }
    }
}
