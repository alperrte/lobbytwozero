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
import static com.ltz.game.infrastructure.persistence.MigrationSchemaSupport.STEAM_STATUS_COLUMNS;
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
        expected.add("172");
        expected.add("173");
        expected.add("174");
        expected.add("175");
        expected.add("176");
        try (var paths = Files.list(CURRENT)) {
            var files = paths.filter(p -> p.toString().endsWith(".sql")).toList();
            assertEquals(16, files.size());
            var versions = new ArrayList<String>();
            for (Path file : files) {
                String sql = Files.readString(file);
                // V174 (DLC ana oyun ilişkisi) gerçek bir şema yükseltmesidir: mevcut tabloyu değiştirir. Başlangıç zinciri (V31–V40, V171–V173) ALTER içermez;
                // Steam durum kolonları (V31) ve fiyat kaynağı kısıtı (V173) bu yüzden tablo tanımlarının içindedir (kullanıcı onaylı tek seferlik istisna).
                String name = file.getFileName().toString();
                if (!name.startsWith("V174__"))
                    assertFalse(ALTER_TABLE.matcher(sql).find(), file.toString());
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
        new JdbcTemplate(old).execute(STEAM_STATUS_COLUMNS);
        assertEquals(10, flyway(current, CURRENT).migrate().migrationsExecuted);
        assertEquals(java.util.stream.IntStream.rangeClosed(31, 40).mapToObj(Integer::toString).toList(),
                new JdbcTemplate(current).queryForList("select version from flyway_schema_history order by installed_rank", String.class));
        assertEquals("V40__create_game_developer_feedback.sql", new JdbcTemplate(current).queryForObject(
                "select script from flyway_schema_history where version='40'", String.class));
        assertEquals("V40__create_game_developer_content.sql", new JdbcTemplate(old).queryForObject(
                "select script from flyway_schema_history where version='40'", String.class));
        assertEquals(schema(old), schema(current));
        // 174 kolon: tarihsel zincirdeki 170 + Steam durum kolonları (free, coming_soon, adult, delisted_at).
        assertEquals(List.of(174, 101, 46, 2, 2, 21), schemaCounts(current));
        assertEquals(seed(old), seed(current));
        assertEquals(21, new JdbcTemplate(current).queryForObject(
                "select count(*) from information_schema.tables where table_schema='public' and table_name<>'flyway_schema_history'",
                Integer.class));
        // Entity eşlemesi son şemayı (V174 parent_game_id) bekler: doğrulama, zincirleri V174'e taşıyan ayrı veritabanlarında yapılır.
        validateHibernate(upgradedToLatest(HISTORY, "old-latest"));
        validateHibernate(upgradedToLatest(CURRENT, "new-latest"));
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
        new JdbcTemplate(old).execute(STEAM_STATUS_COLUMNS);
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

    private DriverManagerDataSource upgradedToLatest(Path source, String name) throws Exception {
        Path chain = copyChain(source, name);
        Files.copy(CURRENT.resolve("V174__add_game_parent_relation.sql"), chain.resolve("V174__add_game_parent_relation.sql"));
        Files.copy(CURRENT.resolve("V175__create_game_localized_descriptions.sql"), chain.resolve("V175__create_game_localized_descriptions.sql"));
        // Tarihsel zincir Steam durum kolonlarına sahip değildir (güncel V31 taşır); entity eşlemesi için yalnız test amaçlı bir adımla eklenir.
        if (source.equals(HISTORY)) Files.writeString(chain.resolve("V176__test_steam_status_columns.sql"), STEAM_STATUS_COLUMNS + ";", StandardCharsets.UTF_8);
        var database = database();
        assertEquals(source.equals(HISTORY) ? 16 : 12, flyway(database, chain).migrate().migrationsExecuted);
        return database;
    }

    private Path copyChain(Path source, String name) throws Exception {
        Path destination = Files.createDirectory(work.resolve(name));
        try (var files = Files.list(source)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".sql")).toList()) {
                String fileName = file.getFileName().toString();
                if (source.equals(CURRENT) && (fileName.startsWith("V171__") || fileName.startsWith("V172__") || fileName.startsWith("V173__")
                        || fileName.startsWith("V174__") || fileName.startsWith("V175__") || fileName.startsWith("V176__"))) continue;
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
