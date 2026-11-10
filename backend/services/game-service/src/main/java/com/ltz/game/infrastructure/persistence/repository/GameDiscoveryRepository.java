package com.ltz.game.infrastructure.persistence.repository;

import com.ltz.game.api.dto.response.GameCardResponse;
import com.ltz.game.api.dto.response.GameDiscoveryResponse;
import com.ltz.game.application.service.DiscoveryQuery;
import com.ltz.game.domain.enums.CatalogDirection;
import com.ltz.game.domain.enums.GameOrigin;
import com.ltz.game.domain.enums.GameType;
import com.ltz.game.domain.enums.MetadataCompleteness;
import com.ltz.game.domain.enums.RatingStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Kart ilişkileri sayfa başına toplu okunur; oyun başına JPA/HTTP çağrısı yoktur. */
@Repository @RequiredArgsConstructor
public class GameDiscoveryRepository {
    private final NamedParameterJdbcTemplate jdbc;
    // Tüm SQL derleme zamanı sabitidir; filtreler isteğe bağlı koşul olarak named parameter ile bağlanır.
    private static final String FILTERED = """
             from games g left join game_rating_projection r on r.game_id=g.id
             where g.status='ACTIVE' and g.game_type='MAIN_GAME' and g.delisted_at is null
             and (:includeAdult = true or g.adult is not true)
             and (cast(:pattern as text) is null or g.normalized_title like cast(:pattern as text) escape '!')
             and (cast(:genre as text) is null or exists(select 1 from game_terms gt join catalog_terms t on t.id=gt.term_id
                 where gt.game_id=g.id and t.kind='GENRE' and t.code=cast(:genre as text)))
             and (cast(:platform as text) is null or exists(select 1 from game_terms gt join catalog_terms t on t.id=gt.term_id
                 where gt.game_id=g.id and t.kind='PLATFORM' and t.code=cast(:platform as text)))
             and (cast(:language as text) is null or exists(select 1 from game_languages l
                 where l.game_id=g.id and l.language_code=cast(:language as text)))
             and (cast(:earlyAccess as boolean) is null or g.early_access = cast(:earlyAccess as boolean))
             and (cast(:releasedAfter as date) is null or g.release_date >= cast(:releasedAfter as date))
             and (cast(:releasedBefore as date) is null or g.release_date <= cast(:releasedBefore as date))
             and (:popularOnly = false or g.editor_rank is not null)
             and (cast(:minRating as numeric) is null or r.average_rating >= cast(:minRating as numeric))
             and (:discountedOnly = false or exists(select 1 from game_price_quotes p where p.game_id=g.id
                 and p.country=:priceCountry and p.currency=:priceCurrency and p.expires_at>current_timestamp
                 and p.current_price<p.regular_price))
            """;
    private static final String COUNT = "select count(*)" + FILTERED;
    private static final String PAGE = """
            select g.*, (select m.url from game_media m where m.game_id=g.id and m.type='COVER'
                order by m.sort_order,m.id limit 1) as cover_url,
                r.average_rating as rating_average, r.rating_count as rating_count
            """ + FILTERED;
    private static final String LIMIT = " limit :limit offset :offset";
    /** Sayfa sorgusu: ortak seçim ve süzgeç + verilen sıralama + sayfalama; sorgu metni her sıralama için tek yerde kurulur. */
    private static String page(String orderBy) { return PAGE + " order by " + orderBy + LIMIT; }
    private static final String TITLE_ASC = page("g.normalized_title asc,g.id asc");
    private static final String TITLE_DESC = page("g.normalized_title desc,g.id asc");
    private static final String RELEASE_ASC = page("g.release_date asc nulls last,g.id asc");
    /** En yeni önce: çıkmış oyunlar yeniden eskiye; henüz çıkmamış (gelecek tarihli, ör. IGDB yer tutucu 2040) ve tarihsiz oyunlar sona. */
    private static final String RELEASE_DESC = page("(g.release_date is null or g.release_date > current_date),g.release_date desc nulls last,g.id asc");
    // Küçük editör sırası daha popülerdir; DESC en popülerden başlar.
    private static final String POPULARITY_ASC = page("g.editor_rank desc nulls last,g.id asc");
    private static final String POPULARITY_DESC = page("g.editor_rank asc nulls last,g.id asc");
    // Puansız oyunlar (projeksiyonda satır yok veya ortalama null) her iki yönde sona gider; eşitlikte gameId bağlar.
    private static final String RATING_ASC = page("r.average_rating asc nulls last,g.id asc");
    private static final String RATING_DESC = page("r.average_rating desc nulls last,g.id asc");

    /** @param syncedAt puan projeksiyonunun son hatasız tarama zamanı; null ise puan durumu UNAVAILABLE'dır. */
    public GameDiscoveryResponse discover(DiscoveryQuery query, Instant syncedAt) {
        Map<String,Object> params = parameters(query);
        long total = Objects.requireNonNull(jdbc.queryForObject(COUNT,params,Long.class));
        List<Row> rows = jdbc.query(pageSql(query),params,(rs,index) -> row(rs));
        List<GameDiscoveryResponse.Item> items = cards(rows,syncedAt != null);
        return new GameDiscoveryResponse(items,query.page(),query.size(),total,query.sort().name(),query.direction().name(),"EDITORIAL",syncedAt);
    }
    private String pageSql(DiscoveryQuery query) {
        boolean ascending = query.direction()==CatalogDirection.ASC;
        return switch(query.sort()) {
            case TITLE -> ascending ? TITLE_ASC : TITLE_DESC;
            case RELEASE_DATE -> ascending ? RELEASE_ASC : RELEASE_DESC;
            case POPULARITY -> ascending ? POPULARITY_ASC : POPULARITY_DESC;
            case RATING -> ascending ? RATING_ASC : RATING_DESC;
        };
    }
    private Map<String,Object> parameters(DiscoveryQuery query) {
        Map<String,Object> params = new HashMap<>();
        params.put("limit",query.size()); params.put("offset",(long)query.page()*query.size());
        params.put("pattern",query.q()==null ? null : query.q().replace("!","!!").replace("%","!%").replace("_","!_")+"%");
        params.put("genre",query.genre()); params.put("platform",query.platform()); params.put("language",query.language());
        params.put("earlyAccess",query.earlyAccess());
        params.put("releasedAfter",query.releasedAfter()); params.put("releasedBefore",query.releasedBefore());
        params.put("popularOnly",query.popularOnly());params.put("includeAdult",query.includeAdult());
        params.put("minRating",query.minRating());
        params.put("discountedOnly",query.discountedOnly());params.put("priceCountry",query.country());params.put("priceCurrency",query.currency());
        return params;
    }
    private List<GameDiscoveryResponse.Item> cards(List<Row> rows,boolean ratingsReady) {
        if (rows.isEmpty()) return List.of();
        Map<UUID,List<GameCardResponse.ExternalReference>> references = new HashMap<>();
        Map<UUID,List<String>> languages = new HashMap<>();
        var params = Map.of("ids",rows.stream().map(Row::id).toList());
        jdbc.query("select game_id,provider,external_id,last_synced_at from game_external_refs where game_id in (:ids) order by provider,external_id", params,
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> references.computeIfAbsent(rs.getObject("game_id",UUID.class),key -> new ArrayList<>())
                        .add(new GameCardResponse.ExternalReference(rs.getString("provider"),rs.getString("external_id"),instant(rs,"last_synced_at"))));
        jdbc.query("select game_id,language_code from game_languages where game_id in (:ids) order by language_code",params,
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> languages.computeIfAbsent(rs.getObject("game_id",UUID.class),key -> new ArrayList<>()).add(rs.getString("language_code")));
        return rows.stream().map(row -> new GameDiscoveryResponse.Item(
                new GameCardResponse(row.id(),row.title(),row.origin(),row.completeness(),GameType.MAIN_GAME,
                        List.copyOf(references.getOrDefault(row.id(),List.of())),row.cover()),
                row.releaseDate(),row.earlyAccess(),row.free(),row.comingSoon(),row.adult(),List.copyOf(languages.getOrDefault(row.id(),List.of())),row.rank(),row.updatedAt(),
                rating(row,ratingsReady))).toList();
    }
    /** Hazır projeksiyonda satır yoksa veya sayı 0 ise doğrulanmış "puan yok"; hazır değilse "alınamadı" (0 değil). */
    private static GameDiscoveryResponse.Rating rating(Row row,boolean ratingsReady) {
        if (!ratingsReady) return new GameDiscoveryResponse.Rating(RatingStatus.UNAVAILABLE,null,null);
        if (row.ratingAverage()==null || row.ratingCount()==null || row.ratingCount()<1)
            return new GameDiscoveryResponse.Rating(RatingStatus.UNRATED,null,0L);
        return new GameDiscoveryResponse.Rating(RatingStatus.RATED,row.ratingAverage(),row.ratingCount());
    }
    private Row row(ResultSet rs) throws SQLException {
        return new Row(rs.getObject("id",UUID.class),rs.getString("title"),GameOrigin.valueOf(rs.getString("origin")),
                MetadataCompleteness.valueOf(rs.getString("metadata_completeness")),rs.getString("cover_url"),
                rs.getObject("release_date",java.time.LocalDate.class),rs.getObject("early_access",Boolean.class),
                rs.getObject("free",Boolean.class),rs.getObject("coming_soon",Boolean.class),rs.getObject("adult",Boolean.class),
                rs.getObject("editor_rank",Integer.class),instant(rs,"popularity_updated_at"),
                rs.getBigDecimal("rating_average"),rs.getObject("rating_count",Long.class));
    }
    private static Instant instant(ResultSet rs,String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
    private record Row(UUID id,String title,GameOrigin origin,MetadataCompleteness completeness,String cover,
            java.time.LocalDate releaseDate,Boolean earlyAccess,Boolean free,Boolean comingSoon,Boolean adult,Integer rank,Instant updatedAt,
            java.math.BigDecimal ratingAverage,Long ratingCount) { }
}
