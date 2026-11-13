package com.ltz.lfp.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ltz.lfp.domain.entity.Listing;

public interface ListingRepository extends JpaRepository<Listing, UUID> {

    /** Sahibin hâlâ geçerli açık ilanları; süresi geçmiş ama henüz işaretlenmemiş ilan sayılmaz. */
    @Query("select count(l) from Listing l where l.ownerId = :ownerId "
            + "and l.status = com.ltz.lfp.domain.enums.ListingStatus.OPEN and l.expiresAt > :now")
    long countOpenByOwner(@Param("ownerId") UUID ownerId, @Param("now") Instant now);

    /** Saatlik oluşturma sınırı: kapatılan ilanlar da sayılır (aç-kapat ile sınır aşılmasın). */
    long countByOwnerIdAndCreatedAtGreaterThanEqual(UUID ownerId, Instant since);
}
