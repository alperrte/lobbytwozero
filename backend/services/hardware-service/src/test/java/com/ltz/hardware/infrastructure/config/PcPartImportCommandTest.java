package com.ltz.hardware.infrastructure.config;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PcPartImportCommandTest {
    private final Map<String, String> development = Map.of("SPRING_PROFILES_ACTIVE", "dev", "DB_HARDWARE_NAME", "hardware_db");

    @Test
    void rejectsProductionOtherDatabasesAndRemoteHosts() {
        assertThatCode(() -> PcPartImportCommand.validateEnvironment(development)).doesNotThrowAnyException();
        for (var environment : java.util.List.of(Map.<String, String>of(),
                Map.of("SPRING_PROFILES_ACTIVE", "prod", "DB_HARDWARE_NAME", "hardware_db"),
                Map.of("SPRING_PROFILES_ACTIVE", "dev", "DB_HARDWARE_NAME", "other_db"),
                Map.of("SPRING_PROFILES_ACTIVE", "dev", "DB_HARDWARE_NAME", "hardware_db", "POSTGRES_HOST", "remote"))) {
            assertThatThrownBy(() -> PcPartImportCommand.validateEnvironment(environment)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void rejectsMissingDirectoryUnknownOptionsAndUnboundedBatchesBeforeDatabaseAccess() {
        for (String[] arguments : java.util.List.of(new String[0], new String[]{"dir", "unknown"},
                new String[]{"dir", "--batch=0"}, new String[]{"dir", "--batch=101"},
                new String[]{"dir", "--execute", "--batch=2", "extra"})) {
            assertThatThrownBy(() -> PcPartImportCommand.execute(arguments, development)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
