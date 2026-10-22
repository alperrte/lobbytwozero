package com.ltz.social.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ltz.social.domain.entity.OutboxEvent;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Hesap silme: yükünde hesabın kimliği geçen tüm olaylar (her durumda). Olay yükleri yalnız kimlik taşıdığından
     * bu kayıtlar kişisel veri sayılır.
     */
    @Modifying
    @Query(value = """
            delete from outbox_events
            where payload ->> 'senderId' = :accountId or payload ->> 'receiverId' = :accountId
               or payload ->> 'userLowId' = :accountId or payload ->> 'userHighId' = :accountId
               or payload ->> 'blockerId' = :accountId or payload ->> 'blockedId' = :accountId""",
            nativeQuery = true)
    int deleteReferencing(@Param("accountId") String accountId);

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
