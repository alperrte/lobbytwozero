package com.ltz.messaging.infrastructure.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ltz.messaging.domain.entity.MessageReadRange;

public interface MessageReadRangeRepository extends JpaRepository<MessageReadRange, UUID> {

    /** Verilen üyelerin {@code seq} mesajını kapsayan ({@code fromSeq <= seq <= toSeq}) okuma aralıkları. */
    @Query("""
            select r from MessageReadRange r
            where r.conversationId = :conversationId and r.userId in :userIds
              and r.fromSeq <= :seq and r.toSeq >= :seq""")
    List<MessageReadRange> findCovering(@Param("conversationId") UUID conversationId,
                                        @Param("userIds") Collection<UUID> userIds, @Param("seq") long seq);
}
