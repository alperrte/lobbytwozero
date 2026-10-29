package com.ltz.game.infrastructure.persistence.repository;
import com.ltz.game.domain.entity.GameAchievementDefinition;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
public interface GameAchievementRepository extends JpaRepository<GameAchievementDefinition,UUID> {
    Page<GameAchievementDefinition> findByGameIdAndVisibleTrue(UUID gameId, Pageable page);
    Page<GameAchievementDefinition> findByGameId(UUID gameId, Pageable page);
    Optional<GameAchievementDefinition> findByGameIdAndCode(UUID gameId,String code);
    long countByGameId(UUID gameId);
}
