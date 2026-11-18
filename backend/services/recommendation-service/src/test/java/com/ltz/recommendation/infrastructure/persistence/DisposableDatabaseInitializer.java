package com.ltz.recommendation.infrastructure.persistence;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Testler geliştirici (DEV) veritabanında ASLA koşmaz. CI runner'ı ({@code ltz.test.external-postgres=true}) kendi geçici
 * PostgreSQL'ini verir; yerelde (pre-push, Sonar, IDE) JVM başına tek bir geçici konteyner açılır. Çözümlenen bağlantı bu
 * iki hedeften biri değilse bağlam hiç kurulmaz; Docker yoksa testler görünür biçimde başarısız olur.
 */
public final class DisposableDatabaseInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    static final String CONTAINER_DATABASE = "recommendation-phase-test";
    static final String CONTAINER_USER = "ltz_recommendation_test";
    private static PostgreSQLContainer sharedContainer;

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        boolean external = Boolean.getBoolean("ltz.test.external-postgres");
        if (!external) {
            TestPropertyValues.of(containerDatasource()).applyTo(context);
        }
        requireDisposable(context.getEnvironment().getProperty("spring.datasource.url", ""),
                context.getEnvironment().getProperty("spring.datasource.username", ""), external);
    }

    /** Konteyner JVM ömrü boyunca açık kalır; Testcontainers (Ryuk) JVM çıkışında temizler. */
    private static synchronized String[] containerDatasource() {
        if (sharedContainer == null) {
            sharedContainer = new PostgreSQLContainer("postgres:17-alpine")
                    .withDatabaseName(CONTAINER_DATABASE).withUsername(CONTAINER_USER);
            sharedContainer.start();
        }
        return new String[] {"spring.datasource.url=" + sharedContainer.getJdbcUrl(),
            "spring.datasource.username=" + sharedContainer.getUsername(),
            "spring.datasource.password=" + sharedContainer.getPassword(),
            // -Pci ile aynı sahte Auth ucu: ortam değişkeni olmadan da bağlam kurulur, gerçek Auth'a çıkılmaz.
            "AUTH_SERVICE_URL=http://127.0.0.1:1"};
    }

    static void requireDisposable(String url, String user, boolean external) {
        boolean container = !external && url.contains("/" + CONTAINER_DATABASE) && user.equals(CONTAINER_USER);
        boolean ci = external && user.equals("ltz_test")
                && url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/recommendation-service");
        if (!container && !ci) {
            throw new IllegalStateException("Recommendation testleri yalnız izole test veritabanında çalışır; "
                    + "geliştirici veritabanı reddedildi.");
        }
    }
}
