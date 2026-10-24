package com.ltz.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;

import org.junit.jupiter.api.Test;

class TestDatabaseSettingsTest {

    private static Properties external() {
        Properties properties = new Properties();
        properties.setProperty("ltz.test.external-postgres", "true");
        properties.setProperty("spring.datasource.url", "jdbc:postgresql://127.0.0.1:65432/auth-service");
        properties.setProperty("spring.datasource.username", "ltz_test");
        properties.setProperty("spring.datasource.password", "ltz_test_only");
        return properties;
    }

    @Test
    void standaloneUsesOwnedTestcontainer() {
        assertFalse(TestDatabaseSettings.useExternal(new Properties()));
    }

    @Test
    void acceptsOnlyExplicitDisposableDatabaseContract() {
        assertTrue(TestDatabaseSettings.useExternal(external()));
    }

    @Test
    void refusesRemoteWrongDatabaseAndInvalidPort() {
        for (String url : new String[] { "jdbc:postgresql://db.invalid:5432/auth-service",
                "jdbc:postgresql://127.0.0.1:65432/auth_db", "jdbc:postgresql://127.0.0.1:0/auth-service",
                "jdbc:postgresql://127.0.0.1:65536/auth-service",
                "jdbc:postgresql://127.0.0.1:65432/auth-service?ssl=false" }) {
            Properties properties = external();
            properties.setProperty("spring.datasource.url", url);
            assertThrows(IllegalStateException.class, () -> TestDatabaseSettings.useExternal(properties));
        }
    }

    @Test
    void refusesUnknownFlagAndCredentialsWithoutEchoingValues() {
        for (String key : new String[] { "ltz.test.external-postgres", "spring.datasource.username",
                "spring.datasource.password" }) {
            Properties properties = external();
            properties.setProperty(key, "synthetic-invalid-marker");
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> TestDatabaseSettings.useExternal(properties));
            assertFalse(exception.getMessage().contains("synthetic-invalid-marker"));
        }
    }
}
