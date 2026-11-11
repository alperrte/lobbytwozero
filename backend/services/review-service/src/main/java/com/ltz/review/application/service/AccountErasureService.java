package com.ltz.review.application.service;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.simple.JdbcClient;
import com.ltz.review.infrastructure.persistence.repository.AccountWriteLock;
import com.ltz.review.infrastructure.persistence.repository.UserSummaryOutbox;

@Service
public class AccountErasureService {
    private final JdbcClient jdbc;
    private final AccountWriteLock accounts;
    private final UserSummaryOutbox outbox;

    public AccountErasureService(JdbcClient jdbc, AccountWriteLock accounts, UserSummaryOutbox outbox) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.outbox = outbox;
    }

    @Transactional(timeout = 4)
    public void erase(UUID accountId) {
        java.util.Objects.requireNonNull(accountId);
        // Karşılıklı beğenilerde iki hesap silmesinin CASCADE kilit sırasını korur.
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended('review:account-erasure',0))").query((rs, row) -> 0).single();
        accounts.block(accountId);
        jdbc.sql("DELETE FROM review_likes WHERE user_id = ?").param(accountId).update();
        jdbc.sql("DELETE FROM game_ratings WHERE user_id = ?").param(accountId).update();
        // Raportörün kendi raporları; silinen yazarın incelemelerine yapılan raporlar reviews silinince CASCADE ile kalkar.
        jdbc.sql("DELETE FROM review_reports WHERE reporter_id = ?").param(accountId).update();
        jdbc.sql("DELETE FROM reviews WHERE user_id = ?").param(accountId).update();
        jdbc.sql("DELETE FROM game_recommendations WHERE user_id = ?").param(accountId).update();
        // REV-10: silinen hesap için bekleyen olay kalmaz ve yenisi üretilmez (User kendi silme akışını yürütür).
        outbox.remove(accountId);
        // REV-13 kota sayaçları: silinen hesabın hiçbir izi kalmasın (yalnız kısa ömürlü silme engeli V54 tutulur).
        jdbc.sql("DELETE FROM review_rate_limits WHERE account_hash = ?").param(AccountWriteLock.hash(accountId)).update();
    }
}
