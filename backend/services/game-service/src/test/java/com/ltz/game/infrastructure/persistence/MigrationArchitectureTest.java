package com.ltz.game.infrastructure.persistence;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static com.ltz.game.infrastructure.persistence.MigrationSchemaSupport.schema;
import static com.ltz.game.infrastructure.persistence.MigrationSchemaSupport.schemaCounts;
import static com.ltz.game.infrastructure.persistence.MigrationSchemaSupport.validateHibernate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Eski tarihçe yalnız test kaynağıdır; DEV'e bağlanılmaz. */
class MigrationArchitectureTest {
    private static final Pattern ALTER_TABLE = Pattern.compile("\\balter\\s+table\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern VERSION_SEPARATOR = Pattern.compile("__");
    private static final Pattern MANIFEST_SEPARATOR = Pattern.compile("  ");
    private static final Path CURRENT = Path.of("src/main/resources/db/migration");
    private static final Path HISTORY = Path.of("src/test/resources/db/history/game-service-v173");
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("architecture");
    @TempDir Path work;

    @BeforeAll static void start() { POSTGRES.start(); }
    @AfterAll static void stop() { POSTGRES.stop(); }

    private DriverManagerDataSource database() {
        String name = "test_" + UUID.randomUUID().toString().replace("-", "");
        var admin = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        new JdbcTemplate(admin).execute("create database " + name);
        return new DriverManagerDataSource(POSTGRES.getJdbcUrl().replace("/architecture", "/" + name),
                POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private Flyway flyway(DriverManagerDataSource source, Path location) {
        var configuration = Flyway.configure().dataSource(source).locations("filesystem:" + location.toAbsolutePath());
        // Bu sınıf tarihsel yeniden numaralandırma eşdeğerliğini V40 sınırında korur.
        // Sonraki yükseltmeler AccountErasureMigrationTest'te ayrıca doğrulanır.
        if (location.equals(CURRENT)) configuration.target("40");
        return configuration.load();
    }

    @Test void productionChainHasNoAlterAndHistoricalFixturesRemainExact() throws Exception {
        List<String> expected = new ArrayList<>(java.util.stream.IntStream.rangeClosed(31, 40).mapToObj(Integer::toString).toList());
        expected.add("171");
        try (var paths = Files.list(CURRENT)) {
            var files = paths.filter(p -> p.toString().endsWith(".sql")).toList();
            assertEquals(11, files.size());
            var versions = new ArrayList<String>();
            for (Path file : files) {
                String sql = Files.readString(file);
                assertFalse(ALTER_TABLE.matcher(sql).find(),
                        file.toString());
                versions.add(VERSION_SEPARATOR.split(file.getFileName().toString())[0].substring(1));
            }
            assertEquals(expected.stream().sorted().toList(), versions.stream().sorted().toList());
        }
        var manifest = Files.readAllLines(HISTORY.resolve("SHA256SUMS"));
        assertEquals(13, manifest.size());
        for (String line : manifest) {
            String[] fields = MANIFEST_SEPARATOR.split(line);
            assertEquals(fields[0], HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(HISTORY.resolve(fields[1])))), fields[1]);
        }
    }

    @Test void freshChainsHaveIdenticalSchemaSeedAndHibernateMappings() throws Exception {
        var old = database();
        var current = database();
        assertEquals(13, flyway(old, HISTORY).migrate().migrationsExecuted);
        assertEquals(10, flyway(current, CURRENT).migrate().migrationsExecuted);
        assertEquals(java.util.stream.IntStream.rangeClosed(31, 40).mapToObj(Integer::toString).toList(),
                new JdbcTemplate(current).queryForList("select version from flyway_schema_history order by installed_rank", String.class));
        assertEquals("V40__create_game_developer_feedback.sql", new JdbcTemplate(current).queryForObject(
                "select script from flyway_schema_history where version='40'", String.class));
        assertEquals("V40__create_game_developer_content.sql", new JdbcTemplate(old).queryForObject(
                "select script from flyway_schema_history where version='40'", String.class));
        assertEquals(schema(old), schema(current));
        assertEquals(List.of(170, 101, 46, 2, 2, 21), schemaCounts(current));
        assertEquals(seed(old), seed(current));
        assertEquals(21, new JdbcTemplate(current).queryForObject(
                "select count(*) from information_schema.tables where table_schema='public' and table_name<>'flyway_schema_history'",
                Integer.class));
        validateHibernate(old);
        validateHibernate(current);
        assertEquals(0, flyway(current, CURRENT).migrate().migrationsExecuted);
        flyway(current, CURRENT).validate();
    }

    @Test void populatedOldHistoryRejectsRewriteWithoutChangingDataOrHistory() {
        var source = database();
        flyway(source, HISTORY).migrate();
        var jdbc = new JdbcTemplate(source);
        UUID id = UUID.randomUUID();
        jdbc.update("insert into games(id,title,normalized_title,created_at,updated_at,metadata_language) "
                + "values (?,'Preserved','preserved',now(),now(),'tr')", id);
        jdbc.update("insert into game_languages(game_id,language_code) values (?,'tr')", id);
        jdbc.update("insert into game_metadata_provenance(game_id,field,source,data_state,recorded_at) "
                + "values (?,'TITLE','ADMIN','AVAILABLE',now())", id);
        List<String> beforeSchema = schema(source);
        List<String> beforeHistory = history(source);
        var beforeData = jdbc.queryForList("select row_to_json(t)::text from games t", String.class);
        Flyway rewritten = flyway(source, CURRENT);
        assertThrows(FlywayValidateException.class, rewritten::migrate);
        assertEquals(beforeSchema, schema(source));
        assertEquals(beforeHistory, history(source));
        assertEquals(beforeData, jdbc.queryForList("select row_to_json(t)::text from games t", String.class));
        assertEquals("tr", jdbc.queryForObject("select language_code from game_languages where game_id=?", String.class, id));
        assertEquals("ADMIN", jdbc.queryForObject("select source from game_metadata_provenance where game_id=?", String.class, id));
    }

    @Test void futureMigrationWorksAfterEitherFreshInstallation() throws Exception {
        var old = database();
        var current = database();
        flyway(old, HISTORY).migrate();
        flyway(current, CURRENT).migrate();
        Path oldFuture = copyChain(HISTORY, "old");
        Path newFuture = copyChain(CURRENT, "new");
        String probe = "create table migration_architecture_probe(id uuid primary key);";
        Files.writeString(oldFuture.resolve("V174__test_probe.sql"), probe, StandardCharsets.UTF_8);
        Files.writeString(newFuture.resolve("V174__test_probe.sql"), probe, StandardCharsets.UTF_8);
        assertEquals(1, flyway(old, oldFuture).migrate().migrationsExecuted);
        assertEquals(1, flyway(current, newFuture).migrate().migrationsExecuted);
        assertEquals(schema(old), schema(current));
    }

    private Path copyChain(Path source, String name) throws Exception {
        Path destination = Files.createDirectory(work.resolve(name));
        try (var files = Files.list(source)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".sql")).toList()) {
                if (source.equals(CURRENT) && file.getFileName().toString().startsWith("V171__")) continue;
                Files.copy(file, destination.resolve(file.getFileName()));
            }
        }
        return destination;
    }

    private List<String> history(DriverManagerDataSource source) {
        return new JdbcTemplate(source).queryForList("select row_to_json(t)::text from "
                + "(select * from flyway_schema_history order by installed_rank) t", String.class);
    }

    private List<String> seed(DriverManagerDataSource source) {
        return new JdbcTemplate(source).queryForList("select row_to_json(t)::text from game_provider_sync_state t", String.class);
    }

}
