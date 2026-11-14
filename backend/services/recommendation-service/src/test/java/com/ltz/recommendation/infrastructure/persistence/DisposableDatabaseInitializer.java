package com.ltz.recommendation.infrastructure.persistence;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/** Yalnız repo CI runner'ının geçici veritabanına bağlanır; geliştirici ENV'sine geri dönmez. */
public class DisposableDatabaseInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext context) {
        String url = context.getEnvironment().getProperty("spring.datasource.url", "");
        String username = context.getEnvironment().getProperty("spring.datasource.username", "");
        if (!Boolean.getBoolean("ltz.test.external-postgres") || !"ltz_test".equals(username)
                || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/recommendation-service")) {
            throw new IllegalStateException("Use the disposable PostgreSQL runner: node pre-push/ci/backend.mjs "
                    + "--modules services/recommendation-service");
        }
    }
}
