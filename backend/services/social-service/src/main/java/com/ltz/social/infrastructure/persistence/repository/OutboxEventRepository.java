package com.ltz.social.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ltz.social.domain.entity.OutboxEvent;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Zamanı gelmiş bekleyen olayları kilitleyerek okur. {@code SKIP LOCKED}, birden fazla relay örneğinin
     * aynı olayı aynı anda almasını önler.
     */
    @Query(value = """
            select * from outbox_events
            where status = 'PENDING' and next_attempt_at <= :now
            order by next_attempt_at, id
            limit :limit
            for update skip locked""", nativeQuery = true)
    List<OutboxEvent> lockDue(@Param("now") Instant now, @Param("limit") int limit);
}
