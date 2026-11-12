package com.ltz.review;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Review testleri tabloları temizler ve rastgele veri yazar; bu yüzden ASLA geliştirici (DEV) veritabanında koşmamalıdır.
 * Bu başlatıcı her Spring test bağlamı açılmadan önce çalışır ({@code META-INF/spring.factories}) ve iki şeyi zorlar:
 * <ul>
 *   <li>CI ({@code -Pci}, {@code ltz.test.external-postgres=true}) kendi izole PostgreSQL'ini verir; yerelde (pre-push, IDE,
 *       {@code mvn test}) JVM başına tek bir geçici PostgreSQL konteyneri açılır ve DB ayarları ona yönlendirilir.</li>
 *   <li>Çözümlenen bağlantı bu iki izole hedeften biri değilse bağlam hiç kurulmaz (Flyway/temizlik çalışmadan hata verir).
 *       Docker yoksa testler görünür biçimde başarısız olur; DEV veritabanına asla geri dönülmez.</li>
 * </ul>
 */
public final class DisposableDatabaseInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    static final String CONTAINER_DATABASE = "review-phase-test";
    static final String CONTAINER_USER = "ltz_review_test";
    private static final String CI_DATABASE = "review-service";
    private static final String CI_USER = "ltz_test";
    private static PostgreSQLContainer sharedContainer;

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        if (!externalPostgres()) {
            TestPropertyValues.of(containerDatasource()).applyTo(context);
        }
        ConfigurableEnvironment environment = context.getEnvironment();
        requireDisposable(environment.getProperty("spring.datasource.url"), environment.getProperty("spring.datasource.username"),
                externalPostgres());
    }

    private static boolean externalPostgres() {
        return Boolean.getBoolean("ltz.test.external-postgres");
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
            "spring.datasource.password=" + sharedContainer.getPassword()};
    }

    /** Yalnız ayrılmış test veritabanları kabul edilir; geliştirici veritabanı (örn. review_db) her koşulda reddedilir. */
    static void requireDisposable(String url, String user, boolean external) {
        boolean container = !external && url != null && url.contains("/" + CONTAINER_DATABASE) && CONTAINER_USER.equals(user);
        boolean ci = external && url != null && url.endsWith("/" + CI_DATABASE) && CI_USER.equals(user);
        if (!container && !ci) {
            throw new IllegalStateException("Review testleri yalnız izole test veritabanında çalışır; geliştirici veritabanı reddedildi.");
        }
    }
}
