package com.ltz.game.infrastructure.persistence.repository;

import com.ltz.game.domain.entity.GameProviderSyncState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface GameProviderSyncStateRepository extends JpaRepository<GameProviderSyncState, UUID> {
    Optional<GameProviderSyncState> findByProvider(String provider);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from GameProviderSyncState s where s.provider = :provider")
    Optional<GameProviderSyncState> lockProvider(@Param("provider") String provider);
}
