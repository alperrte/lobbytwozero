package com.ltz.review.infrastructure.persistence;

import com.ltz.review.application.port.GameCatalogPort;
import com.ltz.review.infrastructure.persistence.repository.RatingSummaryRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** findPage: imleçsiz ilk sayfa ve imleçli devam sayfası aynı sıralamayı (game_id artan) ve dışlayıcı imleci kullanır. */
@SpringBootTest
class RatingSummaryRepositoryTest {
    private static final int LIMIT = 100_000;
    @Autowired RatingSummaryRepository repository;
    @Autowired JdbcClient jdbc;
    @MockitoBean GameCatalogPort games;

    private void rate(UUID game, int rating) {
        jdbc.sql("INSERT INTO game_ratings (id,user_id,game_id,rating,created_at,updated_at) VALUES (gen_random_uuid(),gen_random_uuid(),?,?,now(),now())")
                .params(game, (short) rating).update();
    }

    @Test void pagesAreOrderedByGameIdAndTheCursorIsExclusive() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        // PostgreSQL uuid sırası işaretsiz bayt sırasıdır; küçük harfli onaltılık metin sırasıyla aynıdır (UUID.compareTo işaretlidir).
        UUID first = a.toString().compareTo(b.toString()) < 0 ? a : b;
        UUID second = first.equals(a) ? b : a;
        rate(first, 4);
        rate(first, 6);
        rate(second, 9);

        List<UUID> all = repository.findPage(null, LIMIT).stream().map(row -> row.gameId()).toList();
        assertTrue(all.indexOf(first) >= 0 && all.indexOf(first) < all.indexOf(second), "game_id artan sıra");

        var afterFirst = repository.findPage(first, LIMIT);
        assertFalse(afterFirst.stream().anyMatch(row -> row.gameId().equals(first)), "imleç dışlayıcıdır");
        var secondRow = afterFirst.stream().filter(row -> row.gameId().equals(second)).findFirst().orElseThrow();
        assertEquals(1L, secondRow.ratingCount());

        var firstRow = repository.findPage(null, LIMIT).stream().filter(row -> row.gameId().equals(first)).findFirst().orElseThrow();
        assertEquals(2L, firstRow.ratingCount());
        assertEquals(0, firstRow.averageRating().compareTo(new java.math.BigDecimal("5.00")));
        assertEquals(1, repository.findPage(first, 1).size());
    }
}
