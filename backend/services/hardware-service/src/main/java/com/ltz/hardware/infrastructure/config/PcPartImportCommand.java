package com.ltz.hardware.infrastructure.config;

import com.ltz.hardware.application.mapper.PcPartMapper;
import com.ltz.hardware.application.service.PcPartImportService;
import com.ltz.hardware.infrastructure.client.PcPartDatasetReader;
import com.ltz.hardware.infrastructure.persistence.repository.PcPartImportRepository;
import java.nio.file.Path;
import java.io.IOException;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.ObjectMapper;

/** Separate offline CLI entry point: never registered as a web runner or endpoint. */
public final class PcPartImportCommand {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(PcPartImportCommand.class);
    private static final String POSTGRES = "postgres";
    private record Options(Path directory, boolean dryRun, int batchSize) { }
    private PcPartImportCommand() { }

    public static void main(String[] args) {
        try { execute(args, System.getenv()); }
        catch (Exception exception) {
            LOG.error("PC_PART_IMPORT_FAILED: {}", exception.getClass().getSimpleName());
            System.exit(1);
        }
    }

    static void execute(String[] args, Map<String, String> environment) throws IOException {
        validateEnvironment(environment);
        Options options = options(args);
        var json = new ObjectMapper();
        var reader = new PcPartDatasetReader(json, new PcPartMapper(json));
        var dataset = reader.read(options.directory());
        LOG.atInfo().addArgument(() -> json.writeValueAsString(dataset.files())).log("DATASET_FILES {}");
        var source = new DriverManagerDataSource("jdbc:postgresql://" + environment.getOrDefault("POSTGRES_HOST", POSTGRES)
                + ":" + environment.getOrDefault("POSTGRES_PORT", "5432") + "/hardware_db",
                environment.get("POSTGRES_USER"), environment.get("POSTGRES_PASSWORD"));
        var jdbc = new JdbcTemplate(source);
        if (!"hardware_db".equals(jdbc.queryForObject("SELECT current_database()", String.class))) throw new IllegalStateException("WRONG_DATABASE");
        var flyway = Flyway.configure().dataSource(source).cleanDisabled(true).baselineOnMigrate(false)
                .ignoreMigrationPatterns(options.dryRun() ? new String[]{"*:pending"} : new String[0]).load();
        if (!options.dryRun()) flyway.migrate();
        else flyway.validate();
        var service = new PcPartImportService(new PcPartImportRepository(jdbc, json), new DataSourceTransactionManager(source));
        var result = service.run(dataset, options.batchSize(), options.dryRun());
        LOG.atInfo().addArgument(() -> json.writeValueAsString(result)).log("PC_PART_IMPORT_RESULT {}");
        if (!result.issues().isEmpty() || result.categories().values().stream().anyMatch(c -> c.skipped() > 0)) {
            throw new IllegalStateException("IMPORT_REQUIRES_REVIEW");
        }
    }

    private static Options options(String[] args) {
        if (args.length < 1 || args.length > 3) throw new IllegalArgumentException("DATASET_DIRECTORY_REQUIRED");
        boolean dryRun = true;
        int batchSize = 50;
        for (int index = 1; index < args.length; index++) {
            if ("--execute".equals(args[index])) dryRun = false;
            else if (args[index].startsWith("--batch=")) batchSize = Integer.parseInt(args[index].substring(8));
            else throw new IllegalArgumentException("UNKNOWN_ARGUMENT");
        }
        if (batchSize < 1 || batchSize > 100) throw new IllegalArgumentException("INVALID_BATCH_SIZE");
        return new Options(Path.of(args[0]), dryRun, batchSize);
    }

    static void validateEnvironment(Map<String, String> environment) {
        if (!"dev".equals(environment.get("SPRING_PROFILES_ACTIVE"))
                || !"hardware_db".equals(environment.get("DB_HARDWARE_NAME"))
                || !java.util.Set.of(POSTGRES, "localhost", "127.0.0.1").contains(environment.getOrDefault("POSTGRES_HOST", POSTGRES))) {
            throw new IllegalStateException("LOCAL_DEVELOPMENT_ONLY");
        }
    }
}
