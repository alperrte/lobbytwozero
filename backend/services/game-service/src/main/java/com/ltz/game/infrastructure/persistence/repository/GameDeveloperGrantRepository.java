package com.ltz.game.infrastructure.persistence.repository;

import com.ltz.game.domain.entity.GameDeveloperGrant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameDeveloperGrantRepository extends JpaRepository<GameDeveloperGrant, UUID> {
    Optional<GameDeveloperGrant> findByGameIdAndDeveloperUserIdAndRevokedAtIsNull(UUID gameId, UUID developerUserId);
    boolean existsByGameIdAndDeveloperUserIdAndRevokedAtIsNull(UUID gameId, UUID developerUserId);
    List<GameDeveloperGrant> findByGameIdAndRevokedAtIsNullOrderByGrantedAtAscIdAsc(UUID gameId);
    List<GameDeveloperGrant> findByDeveloperUserIdAndRevokedAtIsNullOrderByGrantedAtAscIdAsc(UUID developerUserId);
}
