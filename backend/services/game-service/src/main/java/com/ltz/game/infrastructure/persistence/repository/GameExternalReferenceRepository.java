package com.ltz.game.infrastructure.persistence.repository;

import com.ltz.game.domain.entity.GameExternalReference;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface GameExternalReferenceRepository extends JpaRepository<GameExternalReference, UUID> {
    @org.springframework.data.jpa.repository.Query("select r from GameExternalReference r join fetch r.game where r.game.id in :ids")
    java.util.List<com.ltz.game.domain.entity.GameExternalReference> findByGameIdIn(
            @org.springframework.data.repository.query.Param("ids") java.util.Collection<UUID> ids);
    Optional<GameExternalReference> findByProviderAndExternalId(String provider, String externalId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from GameExternalReference r where r.provider = :provider and r.externalId = :externalId")
    Optional<GameExternalReference> lockReference(
            @org.springframework.data.repository.query.Param("provider") String provider,
            @org.springframework.data.repository.query.Param("externalId") String externalId);
    long countByProvider(String provider);
    long countByProviderAndGameGameType(String provider, com.ltz.game.domain.enums.GameType gameType);
}
