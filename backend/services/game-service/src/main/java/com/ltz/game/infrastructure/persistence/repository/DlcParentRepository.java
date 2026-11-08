package com.ltz.game.infrastructure.persistence.repository;

import com.ltz.game.domain.entity.Game;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** DLC → ana oyun ilişkisi için salt okunur adaylık ve ana oyun arama sorguları (yazma Game varlığı üzerinden yapılır). */
public interface DlcParentRepository extends Repository<Game, UUID> {
    /** Katalogdaki bir Steam kaydının oyunu: AppID, oyun kimliği, tür ve durum. */
    interface ParentRow { String getAppId(); UUID getGameId(); String getGameType(); String getStatus(); }

    /**
     * İnceleme adayları: ana oyunu henüz bağlanmamış aktif harici Steam DLC kayıtları, AppID azalan (yeni önce) ve verilen AppID'den önce (imleç; Steam doğrulaması bekleyen kayıtlar aynı partide tekrar dönmesin). {@code DLC_PARENT}
     * gözlemi {@code cutoff}'tan yeni olan kayıt (bağlanamamış, bekleme süresinde) çıkar; bağlanan kayıt zaten aday değildir.
     */
    @Query(value = """
        select r.external_id as externalId, r.game_id as gameId
        from game_external_refs r join games g on g.id = r.game_id
        where r.provider = 'STEAM' and g.status = 'ACTIVE' and g.origin = 'EXTERNAL' and g.game_type = 'DLC' and g.parent_game_id is null
        and cast(r.external_id as bigint) < :before
        and not exists (select 1 from game_metadata_provenance p where p.game_id = g.id and p.field = 'DLC_PARENT' and p.recorded_at > :cutoff)
        order by cast(r.external_id as bigint) desc
        limit :limit
        """, nativeQuery = true)
    List<GameExternalReferenceRepository.ClassificationRow> candidates(@Param("cutoff") Instant cutoff, @Param("before") long before, @Param("limit") int limit);

    /** Steam AppID'lerinin katalogdaki oyunları (yoksa satır dönmez). */
    @Query(value = """
        select r.external_id as appId, g.id as gameId, g.game_type as gameType, g.status as status
        from game_external_refs r join games g on g.id = r.game_id
        where r.provider = 'STEAM' and r.external_id in (:ids)
        """, nativeQuery = true)
    List<ParentRow> parentsByAppId(@Param("ids") Collection<String> ids);

    /** Bağlı DLC sayısı ve ilişki istatistiği (raporlama). */
    @Query(value = "select count(*) from games where game_type = 'DLC' and status = 'ACTIVE' and parent_game_id is not null", nativeQuery = true)
    long linkedCount();
}
