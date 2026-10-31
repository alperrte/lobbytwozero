package com.ltz.review.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class ErasureGuardCleanup {
    private final JdbcClient jdbc;

    public ErasureGuardCleanup(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    @Transactional(timeout = 4)
    public void removeExpired() {
        jdbc.sql("DELETE FROM review_erasure_guards WHERE expires_at <= clock_timestamp()").update();
    }
}
