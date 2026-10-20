package com.ltz.game.infrastructure.persistence.repository;
import com.ltz.game.domain.entity.Game;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface GameRepository extends JpaRepository<Game, UUID> {
    @org.springframework.data.jpa.repository.Query("""
        select g.id from Game g where g.status = com.ltz.game.domain.enums.GameStatus.ACTIVE
        and g.origin = com.ltz.game.domain.enums.GameOrigin.EXTERNAL
        and (g.detailsFetchedAt is not null or g.detailsFailureCount > 0)
        and (g.detailsExpiresAt is null or g.detailsExpiresAt <= :now)
        and (g.detailsNextAttemptAt is null or g.detailsNextAttemptAt <= :now)
        and (g.detailsLastErrorCode is null or g.detailsNextAttemptAt is not null)
        order by g.detailsFetchedAt asc nulls first, g.id asc
        """)
    java.util.List<UUID> detailRefreshCandidates(@org.springframework.data.repository.query.Param("now") java.time.Instant now,
            org.springframework.data.domain.Pageable pageable);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select g from Game g where g.id = :id")
    java.util.Optional<Game> lockGame(@org.springframework.data.repository.query.Param("id") UUID id);
    @org.springframework.data.jpa.repository.Query("""
        select g from Game g where g.status = :status and g.normalizedTitle like :pattern escape '!'
        and (g.gameType = :mainType or (:includeUnclassified = true and g.gameType = :unknownType))
        and exists (select r.id from GameExternalReference r where r.game = g and r.provider = 'STEAM')
        """)
    org.springframework.data.domain.Page<Game> searchIndex(
            @org.springframework.data.repository.query.Param("pattern") String pattern,
            @org.springframework.data.repository.query.Param("status") com.ltz.game.domain.enums.GameStatus status,
            @org.springframework.data.repository.query.Param("mainType") com.ltz.game.domain.enums.GameType mainType,
            @org.springframework.data.repository.query.Param("unknownType") com.ltz.game.domain.enums.GameType unknownType,
            @org.springframework.data.repository.query.Param("includeUnclassified") boolean includeUnclassified,
            org.springframework.data.domain.Pageable pageable);
}
