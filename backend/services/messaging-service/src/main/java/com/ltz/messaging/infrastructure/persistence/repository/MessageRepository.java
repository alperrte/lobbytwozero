package com.ltz.messaging.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ltz.messaging.domain.entity.Message;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    Optional<Message> findBySenderIdAndClientMessageId(UUID senderId, String clientMessageId);

    /** Geçmiş: {@code beforeSeq}'ten eski, {@code fromSeq} ve sonrası mesajlar, yeniden eskiye. */
    @Query("""
            select m from Message m
            where m.conversationId = :conversationId and m.seq < :beforeSeq and m.seq >= :fromSeq
            order by m.seq desc""")
    List<Message> findOlder(@Param("conversationId") UUID conversationId, @Param("beforeSeq") long beforeSeq,
                            @Param("fromSeq") long fromSeq, Limit limit);

    /** Yeniden bağlanma: {@code afterSeq}'ten yeni mesajlar, eskiden yeniye (kayıpsız eşitleme). */
    @Query("""
            select m from Message m
            where m.conversationId = :conversationId and m.seq > :afterSeq and m.seq >= :fromSeq
            order by m.seq""")
    List<Message> findNewer(@Param("conversationId") UUID conversationId, @Param("afterSeq") long afterSeq,
                            @Param("fromSeq") long fromSeq, Limit limit);

    /** Sohbet listesi: her sohbetin son mesajı (tek sorgu). */
    @Query("""
            select m from Message m, Conversation c
            where c.id in :conversationIds and m.conversationId = c.id and m.seq = c.lastMessageSeq""")
    List<Message> findLastMessages(@Param("conversationIds") Collection<UUID> conversationIds);

    /** Kullanıcı başına gönderim oranı sınırı. */
    long countBySenderIdAndCreatedAtAfter(UUID senderId, Instant since);
}
