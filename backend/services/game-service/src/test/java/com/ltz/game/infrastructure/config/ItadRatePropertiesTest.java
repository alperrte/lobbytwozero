package com.ltz.game.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/** ITAD hız ayarları: geçersiz değer açılışta reddedilir; teslim edilen DEV ve canlı değerleri doğrudur. */
class ItadRatePropertiesTest {
    private static final Duration WINDOW = Duration.ofMinutes(5);
    private static final Duration WAIT = Duration.ofSeconds(20);
    private static final Duration SHORT_WAIT = Duration.ofSeconds(5);
    private static final Duration INCREASE = Duration.ofMinutes(2);
    private static final Duration NEGATIVE = Duration.ofSeconds(-1);

    private static ItadRateProperties with(int limit, int reserve, int floor) {
        return new ItadRateProperties(limit, reserve, WINDOW, WAIT, SHORT_WAIT, true, floor, INCREASE);
    }

    private static Properties load(String file) throws Exception {
        var properties = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("src/main/resources/" + file), StandardCharsets.UTF_8)) { properties.load(reader); }
        return properties;
    }

    private static ItadRateProperties delivered(Properties properties) {
        String prefix = "ltz.game.pricing.rate.";
        var defaults = ItadRateProperties.defaults();
        return new ItadRateProperties(Integer.parseInt(properties.getProperty(prefix + "limit")), Integer.parseInt(properties.getProperty(prefix + "reserve")),
                defaults.window(), defaults.maxWait(), defaults.interactiveMaxWait(), defaults.adaptive(), Integer.parseInt(properties.getProperty(prefix + "floor")), defaults.increaseInterval());
    }

    @Test void theDefaultsAreTheDocumentedProductionValues() {
        var defaults = ItadRateProperties.defaults();
        assertEquals(850, defaults.limit());
        assertEquals(150, defaults.reserve());
        assertEquals(Duration.ofMinutes(5), defaults.window());
        assertEquals(Duration.ofSeconds(20), defaults.maxWait());
        assertEquals(Duration.ofSeconds(5), defaults.interactiveMaxWait());
        assertTrue(defaults.adaptive());
        assertEquals(30, defaults.floor());
        assertEquals(Duration.ofMinutes(2), defaults.increaseInterval());
    }

    @Test void invalidValuesAreRejectedAtStartup() {
        assertThrows(IllegalArgumentException.class, () -> with(1, 0, 2));
        assertThrows(IllegalArgumentException.class, () -> with(100, 100, 30), "ayrılmış pay limitten küçük olmalı");
        assertThrows(IllegalArgumentException.class, () -> with(100, -1, 30));
        assertThrows(IllegalArgumentException.class, () -> with(100, 20, 1), "taban en az 2");
        assertThrows(IllegalArgumentException.class, () -> with(100, 20, 101), "taban limiti aşamaz");
        assertThrows(IllegalArgumentException.class, () -> new ItadRateProperties(100, 20, Duration.ZERO, WAIT, SHORT_WAIT, true, 30, INCREASE));
        assertThrows(IllegalArgumentException.class, () -> new ItadRateProperties(100, 20, WINDOW, NEGATIVE, SHORT_WAIT, true, 30, INCREASE));
        assertThrows(IllegalArgumentException.class, () -> new ItadRateProperties(100, 20, WINDOW, WAIT, NEGATIVE, true, 30, INCREASE));
        assertThrows(IllegalArgumentException.class, () -> new ItadRateProperties(100, 20, WINDOW, WAIT, SHORT_WAIT, true, 30, Duration.ZERO));
        with(2, 0, 2);
    }

    @Test void theDeliveredDevSettingsAreLowerThanProductionBecauseTheKeyIsSharedAndTheMeasuredThresholdIsLow() throws Exception {
        var dev = delivered(load("application.properties"));
        assertEquals(150, dev.limit());
        assertEquals(30, dev.reserve());
        assertEquals(90, dev.floor());
        var prod = delivered(load("application-prod.properties"));
        assertEquals(850, prod.limit());
        assertEquals(150, prod.reserve());
        assertTrue(dev.limit() < prod.limit());
    }
}
