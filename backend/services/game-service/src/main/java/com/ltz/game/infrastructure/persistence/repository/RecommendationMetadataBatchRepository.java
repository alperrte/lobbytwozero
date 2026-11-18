package com.ltz.game.infrastructure.persistence.repository;

import com.ltz.game.api.dto.response.GameMetadataResponse.Provenance;
import com.ltz.game.api.dto.response.RecommendationMetadataBatchResponse.Field;
import com.ltz.game.api.dto.response.RecommendationMetadataBatchResponse.FieldState;
import com.ltz.game.api.dto.response.RecommendationMetadataBatchResponse.Requirement;
import com.ltz.game.api.dto.response.RecommendationMetadataBatchResponse.Term;
import com.ltz.game.domain.enums.MetadataDataState;
import com.ltz.game.domain.enums.RequirementLevel;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RecommendationMetadataBatchRepository {
    private static final String ELIGIBLE = """
            select g.id, p.field, p.source, p.data_state, p.recorded_at, p.verified_at
            from games g
            left join game_metadata_provenance p on p.game_id=g.id and p.field='SYSTEM_REQUIREMENTS'
            where g.id in (:ids) and g.status='ACTIVE' and g.game_type='MAIN_GAME'
                and g.delisted_at is null and g.adult is false
            """;
    private static final String TERMS = """
            select gt.game_id, t.kind, t.code
            from game_terms gt join catalog_terms t on t.id=gt.term_id
            where gt.game_id in (:ids) and t.kind in ('GENRE','PLATFORM')
            order by gt.game_id, t.code
            """;
    private static final String REQUIREMENTS = """
            select game_id, platform, level, cpu, gpu, ram_mb, storage_mb, os
            from game_system_requirements
            where game_id in (:ids)
            order by game_id, platform, level
            """;
    private static final Field<Integer> UNKNOWN_VRAM = new Field<>(null, FieldState.UNKNOWN);
    private static final Field<String> UNKNOWN_DRIVER = new Field<>(null, FieldState.UNKNOWN);
    private final NamedParameterJdbcTemplate jdbc;

    public Map<UUID, Provenance> eligibleGames(Collection<UUID> ids) {
        Map<UUID, Provenance> games = new HashMap<>();
        jdbc.query(ELIGIBLE, Map.of("ids", ids), (RowCallbackHandler) row ->
                games.put(row.getObject("id", UUID.class), provenance(row)));
        return games;
    }

    public GameTerms terms(Collection<UUID> eligibleIds) {
        Map<UUID, List<Term>> genres = new HashMap<>();
        Map<UUID, List<Term>> platforms = new HashMap<>();
        jdbc.query(TERMS, Map.of("ids", eligibleIds), (RowCallbackHandler) row -> {
            var target = "GENRE".equals(row.getString("kind")) ? genres : platforms;
            target.computeIfAbsent(row.getObject("game_id", UUID.class), key -> new ArrayList<>())
                    .add(new Term(row.getString("code")));
        });
        return new GameTerms(genres, platforms);
    }

    public Map<UUID, List<Requirement>> requirements(Collection<UUID> eligibleIds) {
        Map<UUID, List<Requirement>> requirements = new HashMap<>();
        jdbc.query(REQUIREMENTS, Map.of("ids", eligibleIds), (RowCallbackHandler) row ->
                requirements.computeIfAbsent(row.getObject("game_id", UUID.class), key -> new ArrayList<>())
                        .add(requirement(row)));
        return requirements;
    }

    private static Requirement requirement(ResultSet row) throws SQLException {
        return new Requirement(RequirementLevel.valueOf(row.getString("level")), row.getString("platform"),
                text(row.getString("cpu"), FieldState.UNCOMPARABLE), text(row.getString("gpu"), FieldState.UNCOMPARABLE),
                megabytes(row.getObject("ram_mb", Integer.class)), megabytes(row.getObject("storage_mb", Integer.class)),
                text(row.getString("os"), FieldState.AVAILABLE), UNKNOWN_VRAM, UNKNOWN_DRIVER);
    }

    private static Field<String> text(String value, FieldState present) {
        return value == null || value.isBlank() ? new Field<>(null, FieldState.UNKNOWN) : new Field<>(value, present);
    }

    private static Field<Integer> megabytes(Integer value) {
        return value == null || value <= 0 ? new Field<>(null, FieldState.UNKNOWN) : new Field<>(value, FieldState.AVAILABLE);
    }

    private static Provenance provenance(ResultSet row) throws SQLException {
        String field = row.getString("field");
        if (field == null) {
            return null;
        }
        return new Provenance(field, row.getString("source"), MetadataDataState.valueOf(row.getString("data_state")),
                row.getTimestamp("recorded_at").toInstant(), instant(row.getTimestamp("verified_at")));
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    public record GameTerms(Map<UUID, List<Term>> genres, Map<UUID, List<Term>> platforms) { }
}
