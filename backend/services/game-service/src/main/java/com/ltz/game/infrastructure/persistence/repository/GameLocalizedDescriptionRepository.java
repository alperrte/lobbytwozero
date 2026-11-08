package com.ltz.game.infrastructure.persistence.repository;

import com.ltz.game.domain.entity.GameLocalizedDescription;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameLocalizedDescriptionRepository extends JpaRepository<GameLocalizedDescription, UUID> {
    @Query("select d from GameLocalizedDescription d where d.game.id = :gameId and d.language = :language")
    Optional<GameLocalizedDescription> findByGameAndLanguage(@Param("gameId") UUID gameId, @Param("language") String language);

    @Query("select d from GameLocalizedDescription d where d.game.id in :gameIds and d.language = :language")
    List<GameLocalizedDescription> findByGamesAndLanguage(@Param("gameIds") Collection<UUID> gameIds, @Param("language") String language);
}
