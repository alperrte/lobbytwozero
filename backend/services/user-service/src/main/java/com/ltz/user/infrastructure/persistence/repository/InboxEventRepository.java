package com.ltz.user.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ltz.user.domain.entity.InboxEvent;

public interface InboxEventRepository extends JpaRepository<InboxEvent, UUID> {

    /**
     * Olayı tekilleştirme tablosuna yazar. Aynı {@code eventId} zaten varsa hiçbir şey yapmaz; eşzamanlı iki teslimde
     * ikincisi birincinin işlemi bitene kadar bekler ve 0 döner.
     *
     * @return 1 ise olay yeni, 0 ise daha önce işlenmiş
     */
    @Modifying
    @Query(value = """
            INSERT INTO inbox_events (event_id, event_type, producer, processed_at)
            VALUES (:eventId, :eventType, :producer, :now)
            ON CONFLICT (event_id) DO NOTHING""", nativeQuery = true)
    int recordIfNew(@Param("eventId") UUID eventId, @Param("eventType") String eventType,
            @Param("producer") String producer, @Param("now") Instant now);
}
