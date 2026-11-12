package com.ltz.review;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Geliştirici veritabanına karşı test koşmanın yapısal olarak engellendiğini kanıtlar. */
class DisposableDatabaseInitializerTest {
    @Test void rejectsDeveloperDatabasesInEveryMode() {
        for (boolean external : new boolean[] {false, true}) {
            assertThrows(IllegalStateException.class, () -> DisposableDatabaseInitializer.requireDisposable(
                    "jdbc:postgresql://localhost:5542/review_db", "ltz_dev", external));
            assertThrows(IllegalStateException.class, () -> DisposableDatabaseInitializer.requireDisposable(
                    "jdbc:postgresql://127.0.0.1:5542/review_db", "ltz_test", external));
            assertThrows(IllegalStateException.class, () -> DisposableDatabaseInitializer.requireDisposable(null, null, external));
        }
    }

    @Test void acceptsOnlyTheContainerAndTheCiDatabases() {
        assertDoesNotThrow(() -> DisposableDatabaseInitializer.requireDisposable(
                "jdbc:postgresql://localhost:49152/review-phase-test?loggerLevel=OFF", "ltz_review_test", false));
        assertDoesNotThrow(() -> DisposableDatabaseInitializer.requireDisposable(
                "jdbc:postgresql://127.0.0.1:65432/review-service", "ltz_test", true));
        assertThrows(IllegalStateException.class, () -> DisposableDatabaseInitializer.requireDisposable(
                "jdbc:postgresql://localhost:49152/review-phase-test", "ltz_review_test", true));
        assertThrows(IllegalStateException.class, () -> DisposableDatabaseInitializer.requireDisposable(
                "jdbc:postgresql://127.0.0.1:65432/review-service", "ltz_test", false));
    }
}
