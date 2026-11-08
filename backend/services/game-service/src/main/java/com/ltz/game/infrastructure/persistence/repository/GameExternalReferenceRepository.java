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
    /** Toplu sınıflandırma satırı: Steam AppID ve oyun kimliği. */
    interface ClassificationRow { String getExternalId(); UUID getGameId(); }

    /** Kademeli bekleme koşulu: yakın zamanda ilk kez görülen kayıt kısa, eski kayıt uzun süre yeniden sorulmaz. */
    String COOLDOWN = """
        and not exists (select 1 from game_metadata_provenance p where p.game_id = g.id and p.field = 'GAME_TYPE'
                        and p.data_state = 'UNKNOWN'
                        and p.recorded_at > case when g.created_at > cast(:recentSince as timestamptz) then cast(:cutoffRecent as timestamptz)
                                                 else cast(:cutoffOld as timestamptz) end)
        """;

    /**
     * Toplu sınıflandırma adayları, Steam AppID AZALAN sırada (en yeni önce) ve verilen AppID'den önce: aktif, harici ve türü
     * hâlâ UNKNOWN Steam kayıtları. IGDB'de kanıt bulunamadığı için yakın zamanda UNKNOWN gözlemi kaydedilmiş kayıtlar atlanır
     * (son 30 günde ilk kez görülen: 1 gün; eski: 7 gün). Sayfalama AppID imleciyle yapılır (OFFSET yok); migration gerekmez.
     */
    @org.springframework.data.jpa.repository.Query(value = """
        select r.external_id as externalId, r.game_id as gameId
        from game_external_refs r join games g on g.id = r.game_id
        where r.provider = 'STEAM' and g.status = 'ACTIVE' and g.origin = 'EXTERNAL' and g.game_type = 'UNKNOWN'
        and cast(r.external_id as bigint) < :before
        """ + COOLDOWN + """
        order by cast(r.external_id as bigint) desc
        limit :limit
        """, nativeQuery = true)
    java.util.List<ClassificationRow> classificationBatchDesc(@org.springframework.data.repository.query.Param("before") long before,
            @org.springframework.data.repository.query.Param("recentSince") java.time.Instant recentSince,
            @org.springframework.data.repository.query.Param("cutoffRecent") java.time.Instant cutoffRecent,
            @org.springframework.data.repository.query.Param("cutoffOld") java.time.Instant cutoffOld,
            @org.springframework.data.repository.query.Param("limit") int limit);

    /** Aynı adaylar, AppID ARTAN sırada ve verilen AppID'den sonra: eski kayıtlar da düzenli ilerlesin diye dönüşümlü kullanılır. */
    @org.springframework.data.jpa.repository.Query(value = """
        select r.external_id as externalId, r.game_id as gameId
        from game_external_refs r join games g on g.id = r.game_id
        where r.provider = 'STEAM' and g.status = 'ACTIVE' and g.origin = 'EXTERNAL' and g.game_type = 'UNKNOWN'
        and cast(r.external_id as bigint) > :after
        """ + COOLDOWN + """
        order by cast(r.external_id as bigint) asc
        limit :limit
        """, nativeQuery = true)
    java.util.List<ClassificationRow> classificationBatchAsc(@org.springframework.data.repository.query.Param("after") long after,
            @org.springframework.data.repository.query.Param("recentSince") java.time.Instant recentSince,
            @org.springframework.data.repository.query.Param("cutoffRecent") java.time.Instant cutoffRecent,
            @org.springframework.data.repository.query.Param("cutoffOld") java.time.Instant cutoffOld,
            @org.springframework.data.repository.query.Param("limit") int limit);

    /** Sınıflandırma kuyruğunun büyüklüğü (aynı koşullar); tur başında bir kez sorulur. */
    @org.springframework.data.jpa.repository.Query(value = """
        select count(*)
        from game_external_refs r join games g on g.id = r.game_id
        where r.provider = 'STEAM' and g.status = 'ACTIVE' and g.origin = 'EXTERNAL' and g.game_type = 'UNKNOWN'
        """ + COOLDOWN, nativeQuery = true)
    long countClassificationCandidates(@org.springframework.data.repository.query.Param("recentSince") java.time.Instant recentSince,
            @org.springframework.data.repository.query.Param("cutoffRecent") java.time.Instant cutoffRecent,
            @org.springframework.data.repository.query.Param("cutoffOld") java.time.Instant cutoffOld);

    /**
     * Metadata zenginleştirme adayları: türü doğrulanmış (MAIN_GAME veya DLC), IGDB metadata'sı hiç denenmemiş (IGDB_METADATA
     * gözlemi yok) aktif harici Steam kayıtları; AppID azalan sırada. Bir kez denenen kayıt (veri gelse de gelmese de) çıkar.
     */
    @org.springframework.data.jpa.repository.Query(value = """
        select r.external_id as externalId, r.game_id as gameId
        from game_external_refs r join games g on g.id = r.game_id
        where r.provider = 'STEAM' and g.status = 'ACTIVE' and g.origin = 'EXTERNAL' and g.game_type in ('MAIN_GAME', 'DLC')
        and cast(r.external_id as bigint) < :before
        and not exists (select 1 from game_metadata_provenance p where p.game_id = g.id and p.field = 'IGDB_METADATA')
        order by cast(r.external_id as bigint) desc
        limit :limit
        """, nativeQuery = true)
    java.util.List<ClassificationRow> enrichmentBatch(@org.springframework.data.repository.query.Param("before") long before,
            @org.springframework.data.repository.query.Param("limit") int limit);
    /**
     * PACKAGE inceleme adayları: aktif harici Steam PACKAGE kayıtları; {@code PACKAGE_REVIEW} gözlemi {@code cutoff}'tan yeni
     * olanlar çıkar. AppID artan sırada; incelenen kayıt işaretlendiğinden sıradaki çağrı kendiliğinden ilerler.
     */
    @org.springframework.data.jpa.repository.Query(value = """
        select r.external_id as externalId, r.game_id as gameId
        from game_external_refs r join games g on g.id = r.game_id
        where r.provider = 'STEAM' and g.status = 'ACTIVE' and g.origin = 'EXTERNAL' and g.game_type = 'PACKAGE'
        and not exists (select 1 from game_metadata_provenance p where p.game_id = g.id and p.field = 'PACKAGE_REVIEW' and p.recorded_at > :cutoff)
        order by cast(r.external_id as bigint) asc
        limit :limit
        """, nativeQuery = true)
    java.util.List<ClassificationRow> packageReviewBatch(@org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff,
            @org.springframework.data.repository.query.Param("limit") int limit);
    /**
     * Toplu detay adayları: aktif harici Steam ana oyunları; detayı hiç denenmemiş (ne başarılı ne hatalı). Editör seçimi, sonra
     * yayımlanmış oyunlar (çıkış tarihi yeni olan önce), en sonda henüz çıkmamış ve tarihsizler.
     */
    @org.springframework.data.jpa.repository.Query(value = """
        select r.external_id as externalId, r.game_id as gameId
        from game_external_refs r join games g on g.id = r.game_id
        where r.provider = 'STEAM' and g.status = 'ACTIVE' and g.origin = 'EXTERNAL' and g.game_type = 'MAIN_GAME'
        and g.details_fetched_at is null and g.details_failure_count = 0 and g.details_last_error_code is null
        order by (g.editor_rank is null), (g.release_date is null or g.release_date > current_date), g.release_date desc nulls last, g.id
        limit :limit
        """, nativeQuery = true)
    java.util.List<ClassificationRow> bulkDetailCandidates(@org.springframework.data.repository.query.Param("limit") int limit);
    /**
     * Steam mağaza tamamlama adayları: IGDB detayı denenmiş (veya bulunamamış) ana oyunlardan; sağlayıcı kapağı (yönetici veya
     * Steam), geliştirici, yayıncı ya da dil bilgisi eksik olanlar. {@code STEAM_STORE} gözlemi {@code cutoff}'tan yeni olanlar
     * çıkar. Sıra: editör seçimi, yayımlanmış oyunlar (yeni önce).
     */
    @org.springframework.data.jpa.repository.Query(value = """
        select r.external_id as externalId, r.game_id as gameId
        from game_external_refs r join games g on g.id = r.game_id
        where r.provider = 'STEAM' and g.status = 'ACTIVE' and g.origin = 'EXTERNAL' and g.game_type = 'MAIN_GAME'
        and (g.details_fetched_at is not null or g.details_last_error_code is not null)
        and (not exists (select 1 from game_media m where m.game_id = g.id and m.type = 'COVER' and coalesce(m.provider, '') <> 'IGDB')
             or not exists (select 1 from game_developers d where d.game_id = g.id)
             or not exists (select 1 from game_publishers p where p.game_id = g.id)
             or g.language_data_state <> 'AVAILABLE')
        and not exists (select 1 from game_metadata_provenance v where v.game_id = g.id and v.field = 'STEAM_STORE' and v.recorded_at > :cutoff)
        order by (g.editor_rank is null), g.editor_rank, (g.release_date is null or g.release_date > current_date), g.release_date desc nulls last, g.id
        limit :limit
        """, nativeQuery = true)
    java.util.List<ClassificationRow> storeEnrichmentCandidates(@org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff,
            @org.springframework.data.repository.query.Param("limit") int limit);
    /**
     * Steam toplu mağaza adayları: aktif harici Steam ana oyunları; sağlayıcı kapağı (yönetici/Steam), geliştirici, yayıncı ya da dil bilgisi
     * eksik olanlar. {@code STEAM_ITEMS} gözlemi {@code cutoff}'tan yeni olanlar çıkar. Sıra: editör seçimi, yayımlanmış oyunlar (yeni önce).
     */
    @org.springframework.data.jpa.repository.Query(value = """
        select r.external_id as externalId, r.game_id as gameId
        from game_external_refs r join games g on g.id = r.game_id
        where r.provider = 'STEAM' and g.status = 'ACTIVE' and g.origin = 'EXTERNAL' and g.game_type = 'MAIN_GAME'
        and (not exists (select 1 from game_media m where m.game_id = g.id and m.type = 'COVER' and coalesce(m.provider, '') <> 'IGDB')
             or not exists (select 1 from game_developers d where d.game_id = g.id)
             or not exists (select 1 from game_publishers p where p.game_id = g.id)
             or g.language_data_state <> 'AVAILABLE')
        and not exists (select 1 from game_metadata_provenance v where v.game_id = g.id and v.field = 'STEAM_ITEMS' and v.recorded_at > :cutoff)
        order by (g.editor_rank is null), (g.release_date is null or g.release_date > current_date), g.release_date desc nulls last, g.id
        limit :limit
        """, nativeQuery = true)
    java.util.List<ClassificationRow> storeItemCandidates(@org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff,
            @org.springframework.data.repository.query.Param("limit") int limit);
    /**
     * Yerelleştirilmiş açıklama adayları: aktif harici Steam ana oyunları; {@code marker} gözlemi {@code cutoff}'tan yeni olanlar çıkar. Sıra:
     * editör seçimi, yayımlanmış oyunlar (yeni önce).
     */
    @org.springframework.data.jpa.repository.Query(value = """
        select r.external_id as externalId, r.game_id as gameId
        from game_external_refs r join games g on g.id = r.game_id
        where r.provider = 'STEAM' and g.status = 'ACTIVE' and g.origin = 'EXTERNAL' and g.game_type = 'MAIN_GAME'
        and not exists (select 1 from game_metadata_provenance v where v.game_id = g.id and v.field = :marker and v.recorded_at > :cutoff)
        order by (g.editor_rank is null), (g.release_date is null or g.release_date > current_date), g.release_date desc nulls last, g.id
        limit :limit
        """, nativeQuery = true)
    java.util.List<ClassificationRow> localizedDescriptionCandidates(@org.springframework.data.repository.query.Param("marker") String marker,
            @org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff,
            @org.springframework.data.repository.query.Param("limit") int limit);
    /**
     * Açıklaması boş (null veya yalnız boşluk) aktif harici Steam ana oyunları; {@code marker} gözlemi {@code cutoff}'tan yeni olanlar çıkar.
     * Sıra: editör seçimi, yayımlanmış oyunlar (yeni önce).
     */
    @org.springframework.data.jpa.repository.Query(value = """
        select r.external_id as externalId, r.game_id as gameId
        from game_external_refs r join games g on g.id = r.game_id
        where r.provider = 'STEAM' and g.status = 'ACTIVE' and g.origin = 'EXTERNAL' and g.game_type = 'MAIN_GAME'
        and (g.description is null or btrim(g.description) = '')
        and not exists (select 1 from game_metadata_provenance v where v.game_id = g.id and v.field = :marker and v.recorded_at > :cutoff)
        order by (g.editor_rank is null), (g.release_date is null or g.release_date > current_date), g.release_date desc nulls last, g.id
        limit :limit
        """, nativeQuery = true)
    java.util.List<ClassificationRow> emptyDescriptionCandidates(@org.springframework.data.repository.query.Param("marker") String marker,
            @org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff,
            @org.springframework.data.repository.query.Param("limit") int limit);
    long countByProvider(String provider);
    long countByProviderAndGameGameType(String provider, com.ltz.game.domain.enums.GameType gameType);
}
