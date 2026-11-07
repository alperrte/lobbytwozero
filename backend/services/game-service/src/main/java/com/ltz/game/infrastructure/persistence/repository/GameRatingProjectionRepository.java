package com.ltz.game.infrastructure.persistence.repository;

import com.ltz.game.application.port.ReviewRatingPort.RatingSummary;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * LTZ puan projeksiyonu ve tarama durumu. Sayfa yazımları kısa, ayrı transaction'lardır; Review çağrıları sırasında
 * veritabanı transaction'ı veya bağlantısı tutulmaz. Tek satırlık durum tablosu, çok örnekli dağıtımda aynı anda
 * yalnız bir taramanın çalışmasını (kiralama) ve eski bir taramanın yenisini ezmemesini sağlar.
 */
@Repository @RequiredArgsConstructor
public class GameRatingProjectionRepository {
    private static final String OWNER = "owner";
    private static final UUID STATE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static final String START = """
            update game_rating_sync_state set scan_generation = scan_generation + 1, lease_owner = :owner,
                lease_until = now() + make_interval(secs => cast(:ttl as double precision)), last_attempt_at = now(), version = version + 1
            where id = :id and (lease_until is null or lease_until < now())
            returning scan_generation
            """;
    private static final String EXTEND = """
            update game_rating_sync_state set lease_until = now() + make_interval(secs => cast(:ttl as double precision)), version = version + 1
            where id = :id and lease_owner = :owner and scan_generation = :scan and lease_until >= now()
            """;
    // Bilinmeyen oyunlar (Game kataloğunda olmayan) sessizce atlanır; eski bir tarama yeni taramanın satırını ezmez.
    private static final String UPSERT = """
            insert into game_rating_projection(game_id, average_rating, rating_count, last_seen_scan, synced_at)
            select cast(:gameId as uuid), cast(:average as numeric), cast(:count as bigint), cast(:scan as bigint),
                cast(:syncedAt as timestamptz)
            where exists(select 1 from games where id = cast(:gameId as uuid))
            on conflict (game_id) do update set average_rating = excluded.average_rating,
                rating_count = excluded.rating_count, last_seen_scan = excluded.last_seen_scan,
                synced_at = excluded.synced_at, version = game_rating_projection.version + 1
            where game_rating_projection.last_seen_scan <= excluded.last_seen_scan
            """;
    private static final String COMPLETE = """
            update game_rating_sync_state set last_completed_scan = :scan, last_completed_at = now(),
                last_error_code = null, lease_owner = null, lease_until = null, version = version + 1
            where id = :id and lease_owner = :owner and scan_generation = :scan and lease_until >= now()
            """;
    // Hatasız tamamlanan taramada görülmeyen satırlar puansız yapılır (son puanı hesap silmeyle kalkan oyun dahil).
    private static final String ZERO_UNSEEN = """
            update game_rating_projection set average_rating = null, rating_count = 0, last_seen_scan = :scan,
                synced_at = now(), version = version + 1
            where last_seen_scan < :scan and (average_rating is not null or rating_count <> 0)
            """;
    private static final String FAIL = """
            update game_rating_sync_state set last_error_code = :code, lease_owner = null, lease_until = null,
                version = version + 1
            where id = :id and lease_owner = :owner and scan_generation = :scan
            """;

    private final NamedParameterJdbcTemplate jdbc;

    /** Kiralamayı alırsa yeni tarama sayacını döndürür; başka bir tarama sürüyorsa boş döner. */
    @Transactional
    public OptionalLong startScan(UUID owner, Duration leaseTtl) {
        List<Long> scans = jdbc.query(START, new MapSqlParameterSource("id", STATE_ID).addValue(OWNER, owner)
                .addValue("ttl", seconds(leaseTtl)), (rs, row) -> rs.getLong(1));
        return scans.isEmpty() ? OptionalLong.empty() : OptionalLong.of(scans.getFirst());
    }

    /** Kiralama hâlâ bu taramadaysa uzatır; kaybedildiyse false döner ve tarama bırakılmalıdır. */
    @Transactional
    public boolean extendLease(UUID owner, long scan, Duration leaseTtl) {
        return jdbc.update(EXTEND, new MapSqlParameterSource("id", STATE_ID).addValue(OWNER, owner)
                .addValue("scan", scan).addValue("ttl", seconds(leaseTtl))) == 1;
    }

    /** Bir sayfayı tek transaction'da yazar. Game kataloğunda olmayan oyunlar atlanır. */
    @Transactional
    public void upsertPage(long scan, List<RatingSummary> items, Instant syncedAt) {
        if (items.isEmpty()) {
            return;
        }
        MapSqlParameterSource[] batch = new MapSqlParameterSource[items.size()];
        for (int index = 0; index < batch.length; index++) {
            RatingSummary item = items.get(index);
            batch[index] = new MapSqlParameterSource("gameId", item.gameId())
                    .addValue("average", item.averageRating())
                    .addValue("count", item.ratingCount())
                    .addValue("scan", scan)
                    .addValue("syncedAt", Timestamp.from(syncedAt));
        }
        jdbc.batchUpdate(UPSERT, batch);
    }

    /**
     * Taramayı hatasız tamamlandı olarak kaydeder ve görülmeyen satırları puansız yapar (tek transaction).
     * Kiralama kaybedildiyse hiçbir şey değişmez ve false döner.
     */
    @Transactional
    public boolean complete(UUID owner, long scan) {
        int updated = jdbc.update(COMPLETE, new MapSqlParameterSource("id", STATE_ID).addValue(OWNER, owner)
                .addValue("scan", scan));
        if (updated != 1) {
            return false;
        }
        jdbc.update(ZERO_UNSEEN, new MapSqlParameterSource("scan", scan));
        return true;
    }

    /** Yarıda kesilen taramayı kaydeder; hiçbir projeksiyon satırı sıfırlanmaz, kiralama bırakılır. */
    @Transactional
    public void fail(UUID owner, long scan, String code) {
        jdbc.update(FAIL, new MapSqlParameterSource("id", STATE_ID).addValue(OWNER, owner)
                .addValue("scan", scan).addValue("code", code));
    }

    /** Son hatasız tamamlanan taramanın zamanı; hiç tamamlanmadıysa boş (projeksiyon "hazır değil"). */
    public Optional<Instant> lastCompletedAt() {
        List<Timestamp> values = jdbc.query("select last_completed_at from game_rating_sync_state where id = :id",
                Map.of("id", STATE_ID), (rs, row) -> rs.getTimestamp(1));
        return values.isEmpty() || values.getFirst() == null ? Optional.empty() : Optional.of(values.getFirst().toInstant());
    }

    private static double seconds(Duration duration) {
        return duration.toMillis() / 1000.0;
    }
}
