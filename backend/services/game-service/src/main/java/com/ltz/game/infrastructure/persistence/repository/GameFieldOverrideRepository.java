package com.ltz.game.infrastructure.persistence.repository;

import com.ltz.game.domain.entity.GameFieldOverride;
import com.ltz.game.domain.enums.OverridableField;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameFieldOverrideRepository extends JpaRepository<GameFieldOverride, UUID> {
    List<GameFieldOverride> findByGameIdOrderByFieldAsc(UUID gameId);
    Optional<GameFieldOverride> findByGameIdAndField(UUID gameId, OverridableField field);

    /** Katalog sayfasındaki sağlayıcı kimlikleri için kilitli alanlar tek sorguyla okunur. */
    @Query("""
        select r.externalId as externalId, o.field as field from GameExternalReference r, GameFieldOverride o
        where r.provider = :provider and r.externalId in :externalIds and o.gameId = r.game.id
        """)
    List<LockedField> lockedFields(@Param("provider") String provider, @Param("externalIds") Collection<String> externalIds);

    interface LockedField {
        String getExternalId();
        OverridableField getField();
    }
}
