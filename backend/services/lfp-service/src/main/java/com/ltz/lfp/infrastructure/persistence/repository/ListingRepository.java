package com.ltz.lfp.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ltz.lfp.domain.entity.Listing;

public interface ListingRepository extends JpaRepository<Listing, UUID>, JpaSpecificationExecutor<Listing> {

    /** Sahibin hâlâ geçerli açık ilanları; süresi geçmiş ama henüz işaretlenmemiş ilan sayılmaz. */
    @Query("select count(l) from Listing l where l.ownerId = :ownerId "
            + "and l.status = com.ltz.lfp.domain.enums.ListingStatus.OPEN and l.expiresAt > :now")
    long countOpenByOwner(@Param("ownerId") UUID ownerId, @Param("now") Instant now);

    /** Saatlik oluşturma sınırı: kapatılan ilanlar da sayılır (aç-kapat ile sınır aşılmasın). */
    long countByOwnerIdAndCreatedAtGreaterThanEqual(UUID ownerId, Instant since);

    /**
     * LFP-15: süresi dolan en çok {@code batchSize} açık ilanı {@code EXPIRED} yapar. Satırlar
     * {@code FOR UPDATE SKIP LOCKED} ile seçilir: birden çok örnek aynı anda çalışsa da aynı ilanı iki kez işlemez ve
     * sahibin o an güncellediği satırı beklemez. Sürüm artar; aynı anda gelen sahip güncellemesi 409 alır.
     *
     * @return işaretlenen ilan sayısı
     */
    @Modifying
    @Query(value = "update lfp_listings set status = 'EXPIRED', updated_at = :now, version = version + 1 "
            + "where id in (select id from lfp_listings where status = 'OPEN' and expires_at <= :now "
            + "order by expires_at limit :batchSize for update skip locked)", nativeQuery = true)
    int expireDue(@Param("now") Instant now, @Param("batchSize") int batchSize);

    /**
     * Hesap silme (LFP-18): sahibin tüm ilanları, her durumda. Roller {@code fk_lfp_listing_roles_listing}
     * {@code ON DELETE CASCADE} ile aynı ifadede silinir (native sorgu; JPA bulk silme koleksiyon tablosunu atlayabilirdi).
     *
     * @return silinen ilan sayısı
     */
    @Modifying
    @Query(value = "delete from lfp_listings where owner_id = :ownerId", nativeQuery = true)
    int deleteAllOwnedBy(@Param("ownerId") UUID ownerId);
}
