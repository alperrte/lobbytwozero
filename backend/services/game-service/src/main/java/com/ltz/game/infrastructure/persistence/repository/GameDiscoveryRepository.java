package com.ltz.game.infrastructure.persistence.repository;

import com.ltz.game.api.dto.response.GameCardResponse;
import com.ltz.game.api.dto.response.GameDiscoveryResponse;
import com.ltz.game.application.service.DiscoveryQuery;
import com.ltz.game.domain.enums.CatalogDirection;
import com.ltz.game.domain.enums.GameOrigin;
import com.ltz.game.domain.enums.GameType;
import com.ltz.game.domain.enums.MetadataCompleteness;
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
             from games g where g.status='ACTIVE' and g.game_type='MAIN_GAME'
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
            """;
    private static final String COUNT = "select count(*)" + FILTERED;
    private static final String PAGE = """
            select g.*, (select m.url from game_media m where m.game_id=g.id and m.type='COVER'
                order by m.sort_order,m.id limit 1) as cover_url
            """ + FILTERED;
    private static final String LIMIT = " limit :limit offset :offset";
    private static final String TITLE_ASC = PAGE + " order by g.normalized_title asc,g.id asc" + LIMIT;
    private static final String TITLE_DESC = PAGE + " order by g.normalized_title desc,g.id asc" + LIMIT;
    private static final String RELEASE_ASC = PAGE + " order by g.release_date asc nulls last,g.id asc" + LIMIT;
    private static final String RELEASE_DESC = PAGE + " order by g.release_date desc nulls last,g.id asc" + LIMIT;
    // Küçük editör sırası daha popülerdir; DESC en popülerden başlar.
    private static final String POPULARITY_ASC = PAGE + " order by g.editor_rank desc nulls last,g.id asc" + LIMIT;
    private static final String POPULARITY_DESC = PAGE + " order by g.editor_rank asc nulls last,g.id asc" + LIMIT;

    public GameDiscoveryResponse discover(DiscoveryQuery query) {
        Map<String,Object> params = parameters(query);
        long total = Objects.requireNonNull(jdbc.queryForObject(COUNT,params,Long.class));
        List<Row> rows = jdbc.query(pageSql(query),params,(rs,index) -> row(rs));
        List<GameDiscoveryResponse.Item> items = cards(rows);
        return new GameDiscoveryResponse(items,query.page(),query.size(),total,query.sort().name(),query.direction().name(),"EDITORIAL");
    }
    private String pageSql(DiscoveryQuery query) {
        boolean ascending = query.direction()==CatalogDirection.ASC;
        return switch(query.sort()) {
            case TITLE -> ascending ? TITLE_ASC : TITLE_DESC;
            case RELEASE_DATE -> ascending ? RELEASE_ASC : RELEASE_DESC;
            case POPULARITY -> ascending ? POPULARITY_ASC : POPULARITY_DESC;
            case RATING -> throw new com.ltz.game.domain.exception.CatalogRatingUnavailableException();
        };
    }
    private Map<String,Object> parameters(DiscoveryQuery query) {
        Map<String,Object> params = new HashMap<>();
        params.put("limit",query.size()); params.put("offset",(long)query.page()*query.size());
        params.put("pattern",query.q()==null ? null : query.q().replace("!","!!").replace("%","!%").replace("_","!_")+"%");
        params.put("genre",query.genre()); params.put("platform",query.platform()); params.put("language",query.language());
        params.put("earlyAccess",query.earlyAccess());
        params.put("releasedAfter",query.releasedAfter()); params.put("releasedBefore",query.releasedBefore());
        params.put("popularOnly",query.popularOnly());
        return params;
    }
    private List<GameDiscoveryResponse.Item> cards(List<Row> rows) {
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
                row.releaseDate(),row.earlyAccess(),List.copyOf(languages.getOrDefault(row.id(),List.of())),row.rank(),row.updatedAt())).toList();
    }
    private Row row(ResultSet rs) throws SQLException {
        return new Row(rs.getObject("id",UUID.class),rs.getString("title"),GameOrigin.valueOf(rs.getString("origin")),
                MetadataCompleteness.valueOf(rs.getString("metadata_completeness")),rs.getString("cover_url"),
                rs.getObject("release_date",java.time.LocalDate.class),rs.getObject("early_access",Boolean.class),
                rs.getObject("editor_rank",Integer.class),instant(rs,"popularity_updated_at"));
    }
    private static Instant instant(ResultSet rs,String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
    private record Row(UUID id,String title,GameOrigin origin,MetadataCompleteness completeness,String cover,
            java.time.LocalDate releaseDate,Boolean earlyAccess,Integer rank,Instant updatedAt) { }
}
