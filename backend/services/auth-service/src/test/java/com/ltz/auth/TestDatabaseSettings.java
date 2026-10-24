package com.ltz.auth;

import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** CI runner owns this disposable database. Standalone tests keep their Testcontainer. */
final class TestDatabaseSettings {

    private static final Pattern URL = Pattern.compile("jdbc:postgresql://127\\.0\\.0\\.1:(\\d{1,5})/auth-service");

    private TestDatabaseSettings() {
    }

    static boolean useExternal(Properties properties) {
        String enabled = properties.getProperty("ltz.test.external-postgres", "false");
        if ("false".equals(enabled)) {
            return false;
        }
        Matcher url = URL.matcher(properties.getProperty("spring.datasource.url", ""));
        if (!"true".equals(enabled) || !url.matches()
                || Integer.parseInt(url.group(1)) < 1 || Integer.parseInt(url.group(1)) > 65535
                || !"ltz_test".equals(properties.getProperty("spring.datasource.username"))
                || !"ltz_test_only".equals(properties.getProperty("spring.datasource.password"))) {
            // Never echo supplied URLs or credentials in failure messages.
            throw new IllegalStateException("External test database must match the isolated CI contract");
        }
        return true;
    }
}
