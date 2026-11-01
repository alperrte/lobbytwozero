package com.ltz.messaging.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ltz.messaging.domain.entity.Conversation;

import jakarta.persistence.LockModeType;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    /** Grup üyeliği değişikliklerini (ekleme, çıkarma, rol, ayrılma, sahip devri) sohbet başına sıraya koyar. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversation c where c.id = :id")
    Optional<Conversation> findByIdForUpdate(@Param("id") UUID id);

    Optional<Conversation> findByDirectKey(String directKey);

    /** Kullanıcının aktif üyesi olduğu sohbetler, son etkinlikten eskiye (ilk sayfa). */
    @Query("""
            select c from Conversation c, ConversationMember m
            where m.id.conversationId = c.id and m.id.userId = :userId and m.leftAt is null
            order by c.lastActivityAt desc, c.id desc""")
    List<Conversation> findFirstPageOf(@Param("userId") UUID userId, Limit limit);

    @Query("""
            select c from Conversation c, ConversationMember m
            where m.id.conversationId = c.id and m.id.userId = :userId and m.leftAt is null
              and (c.lastActivityAt < :activityAt or (c.lastActivityAt = :activityAt and c.id < :id))
            order by c.lastActivityAt desc, c.id desc""")
    List<Conversation> findPageOfAfter(@Param("userId") UUID userId, @Param("activityAt") Instant activityAt,
                                       @Param("id") UUID id, Limit limit);
}
