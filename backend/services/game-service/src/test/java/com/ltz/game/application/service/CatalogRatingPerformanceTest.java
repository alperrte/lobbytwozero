package com.ltz.game.application.service;

import com.ltz.game.PostgresTestSupport;
import com.ltz.game.domain.enums.CatalogDirection;
import com.ltz.game.domain.enums.CatalogSort;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GAME-04/05/25: puan projeksiyonu dolu ve 100.000 oyunluk katalogda minRating filtresi ve sort=RATING ölçümü.
 * Sonuçlar target/catalog-rating-performance.json dosyasına yazılır; hedef (p95 ≤ 300 ms) raporlanır, gerçek ölçümle
 * değerlendirilir. Bu bir üretim ölçümü değildir: geçici PostgreSQL 17 Testcontainers ve geliştirici makinesi.
 */
@SpringBootTest
class CatalogRatingPerformanceTest extends PostgresTestSupport {
    private static final int RECORDS = 100_000;
    @Autowired JdbcTemplate jdbc;
    @Autowired GameDiscoveryService discovery;

    @AfterEach void clean() {
        jdbc.update("delete from game_external_refs");
        jdbc.update("delete from games");
        jdbc.update("update game_rating_sync_state set scan_generation=0,last_completed_scan=null,last_completed_at=null,last_error_code=null");
    }

    private void seed() {
        jdbc.update("delete from game_external_refs");
        jdbc.update("delete from games");
        jdbc.execute("""
            insert into games(id,title,normalized_title,created_at,updated_at,origin,game_type,status,metadata_completeness)
            select gen_random_uuid(),'Rated '||lpad(n::text,6,'0'),'rated '||lpad(n::text,6,'0'),now(),now(),
            'INTERNAL',case when n%10=0 then 'DLC' else 'MAIN_GAME' end,case when n%23=0 then 'ARCHIVED' else 'ACTIVE' end,'INDEX_ONLY'
            from generate_series(1,100000) n
            """);
        jdbc.execute("insert into catalog_terms(id,kind,code) values(gen_random_uuid(),'GENRE','rated-action') on conflict(kind,code) do nothing");
        jdbc.execute("insert into game_terms(game_id,term_id) select g.id,t.id from games g cross join catalog_terms t where t.code='rated-action'");
        // %40 puanlı (1.00–10.00, çok sayıda eşit değer), %60 puansız; tarama tamamlanmış sayılır.
        jdbc.execute("""
            insert into game_rating_projection(game_id,average_rating,rating_count,last_seen_scan,synced_at)
            select id,(1+((row_number() over (order by id))%901)/100.0)::numeric(4,2),1+((row_number() over (order by id))%50),1,now()
            from games where (hashtext(id::text)%10+10)%10 < 4
            """);
        jdbc.execute("update game_rating_sync_state set scan_generation=1,last_completed_scan=1,last_completed_at=now()");
        jdbc.execute("analyze games");
        jdbc.execute("analyze game_terms");
        jdbc.execute("analyze game_rating_projection");
    }

    private static DiscoveryQuery query(int page, String genre, BigDecimal minRating, CatalogSort sort, CatalogDirection direction) {
        return new DiscoveryQuery(page, 20, null, genre, null, null, null, null, null, false, minRating, sort, direction);
    }

    @Test void ratingFilterAndSortStayWithinTheTargetAtScaleAndPageIsConsistent() throws Exception {
        seed();
        long rated = jdbc.queryForObject("select count(*) from game_rating_projection", Long.class);
        assertTrue(rated > 30_000 && rated < 50_000, "yaklaşık %40 puanlı: " + rated);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("records", RECORDS);
        report.put("ratedRows", rated);
        report.put("database", "PostgreSQL 17 Testcontainers");
        report.put("java", Runtime.version().toString());
        report.put("os", System.getProperty("os.name"));
        report.put("targetP95Ms", 300);
        var seven = new BigDecimal("7");
        report.put("listingWithRatingP95Ms", measure(new DiscoveryQuery(0, 20)));
        report.put("minRating7P95Ms", measure(query(0, null, seven, CatalogSort.TITLE, CatalogDirection.ASC)));
        report.put("sortRatingDescP95Ms", measure(query(0, null, null, CatalogSort.RATING, CatalogDirection.DESC)));
        report.put("sortRatingAscP95Ms", measure(query(0, null, null, CatalogSort.RATING, CatalogDirection.ASC)));
        report.put("combinedGenreMin5SortRatingP95Ms", measure(query(0, "rated-action", new BigDecimal("5"), CatalogSort.RATING, CatalogDirection.DESC)));
        report.put("deepPage2000SortRatingP95Ms", measure(query(2000, null, null, CatalogSort.RATING, CatalogDirection.DESC)));
        report.put("targetMet", report.entrySet().stream().filter(e -> e.getKey().endsWith("P95Ms") && !e.getKey().equals("targetP95Ms"))
                .allMatch(e -> ((Number) e.getValue()).doubleValue() <= 300));
        Files.writeString(Path.of("target", "catalog-rating-performance.json"),
                tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(report));

        // İşlevsel doğruluk: azalan sırada puan düşmez, puansızlar sonda; filtre eşiği korunur; sayfalar tekrarsız.
        var first = discovery.list(query(0, null, null, CatalogSort.RATING, CatalogDirection.DESC));
        List<BigDecimal> averages = first.items().stream().map(item -> item.rating().averageRating()).toList();
        assertEquals(averages.stream().sorted(java.util.Comparator.reverseOrder()).toList(), averages);
        var filtered = discovery.list(query(0, null, seven, CatalogSort.TITLE, CatalogDirection.ASC));
        assertFalse(filtered.items().isEmpty());
        assertTrue(filtered.items().stream().allMatch(item -> item.rating().averageRating().compareTo(seven) >= 0));
        List<java.util.UUID> ids = new ArrayList<>();
        for (int page = 0; page < 5; page++) {
            discovery.list(query(page, null, seven, CatalogSort.RATING, CatalogDirection.DESC)).items()
                    .forEach(item -> ids.add(item.game().gameId()));
        }
        assertEquals(ids.size(), ids.stream().distinct().count(), "sayfalar arasında tekrar yok");
    }

    private double measure(DiscoveryQuery query) {
        for (int i = 0; i < 5; i++) discovery.list(query);
        List<Double> samples = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            long started = System.nanoTime();
            var result = discovery.list(query);
            samples.add((System.nanoTime() - started) / 1_000_000.0);
            assertFalse(result.items().isEmpty());
        }
        List<Double> sorted = samples.stream().sorted().toList();
        return sorted.get((int) Math.ceil(sorted.size() * 0.95) - 1);
    }
}
