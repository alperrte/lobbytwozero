package com.ltz.game.infrastructure.persistence.repository;

import com.ltz.game.domain.entity.GameDeveloperMedia;
import com.ltz.game.domain.enums.ContentStatus;
import com.ltz.game.domain.enums.DeveloperMediaType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameDeveloperMediaRepository extends JpaRepository<GameDeveloperMedia, UUID> {
    List<GameDeveloperMedia> findByGameIdAndStatusOrderByCreatedAtAscIdAsc(UUID gameId, ContentStatus status);
    List<GameDeveloperMedia> findByGameIdAndStatusNotOrderByCreatedAtAscIdAsc(UUID gameId, ContentStatus status);
    Optional<GameDeveloperMedia> findByIdAndGameId(UUID id, UUID gameId);
    long countByGameIdAndTypeAndStatusNot(UUID gameId, DeveloperMediaType type, ContentStatus status);
}
