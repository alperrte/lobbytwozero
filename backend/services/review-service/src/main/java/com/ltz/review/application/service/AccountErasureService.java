package com.ltz.review.application.service;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.simple.JdbcClient;
import com.ltz.review.infrastructure.persistence.repository.AccountWriteLock;

@Service
public class AccountErasureService {
    private final JdbcClient jdbc;
    private final AccountWriteLock accounts;

    public AccountErasureService(JdbcClient jdbc, AccountWriteLock accounts) {
        this.jdbc = jdbc;
        this.accounts = accounts;
    }

    @Transactional(timeout = 4)
    public void erase(UUID accountId) {
        java.util.Objects.requireNonNull(accountId);
        // Karşılıklı beğenilerde iki hesap silmesinin CASCADE kilit sırasını korur.
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended('review:account-erasure',0))").query((rs, row) -> 0).single();
        accounts.block(accountId);
        jdbc.sql("DELETE FROM review_likes WHERE user_id = ?").param(accountId).update();
        jdbc.sql("DELETE FROM game_ratings WHERE user_id = ?").param(accountId).update();
        jdbc.sql("DELETE FROM reviews WHERE user_id = ?").param(accountId).update();
        jdbc.sql("DELETE FROM game_recommendations WHERE user_id = ?").param(accountId).update();
    }
}
