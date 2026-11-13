package com.ltz.messaging.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ltz.messaging.domain.entity.OutboxEvent;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Zamanı gelmiş bekleyen olayları kilitleyerek okur. {@code SKIP LOCKED}, birden fazla örneğin relay'inin aynı olayı
     * aynı anda almasını önler.
     */
    @Query(value = """
            select * from outbox_events
            where status = 'PENDING' and next_attempt_at <= :now
            order by next_attempt_at, id
            limit :limit
            for update skip locked""", nativeQuery = true)
    List<OutboxEvent> lockDue(@Param("now") Instant now, @Param("limit") int limit);

    /** Saklama süresini aşan olayları (her durumda) en eskiden başlayarak en fazla {@code limit} adet siler. */
    @Modifying
    @Query(value = """
            delete from outbox_events
            where id in (
                select id from outbox_events
                where occurred_at < :cutoff
                order by occurred_at
                limit :limit
                for update skip locked)""", nativeQuery = true)
    int deleteOccurredBefore(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}
