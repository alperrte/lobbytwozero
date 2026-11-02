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

    /** Mesaj yalnız verilen sohbetteyse döner (başka sohbetin mesajı bulunmaz). */
    Optional<Message> findByIdAndConversationId(UUID id, UUID conversationId);

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

    /**
     * Okundu bilgisinin alıcıları (MSG-11): {@code (fromExclusive, toInclusive]} aralığında mesajı olan göndericiler ve
     * aralıktaki en büyük kendi seq'leri, {@code [senderId, max(seq)]}. Okuyucu ({@code excludedSender}) hariçtir.
     */
    @Query("""
            select m.senderId, max(m.seq) from Message m
            where m.conversationId = :conversationId and m.seq > :fromExclusive and m.seq <= :toInclusive
              and m.senderId <> :excludedSender
            group by m.senderId""")
    List<Object[]> findOwnMaxSeqInRange(@Param("conversationId") UUID conversationId,
                                        @Param("fromExclusive") long fromExclusive,
                                        @Param("toInclusive") long toInclusive,
                                        @Param("excludedSender") UUID excludedSender);

    /** Göndericinin {@code upTo} ve öncesindeki en büyük kendi seq'i (su seviyesini kendi mesajlarına kırpar). */
    @Query("""
            select max(m.seq) from Message m
            where m.conversationId = :conversationId and m.senderId = :senderId and m.seq <= :upTo""")
    Optional<Long> findOwnMaxSeqUpTo(@Param("conversationId") UUID conversationId, @Param("senderId") UUID senderId,
                                     @Param("upTo") long upTo);

    /** Kullanıcı başına gönderim oranı sınırı. */
    long countBySenderIdAndCreatedAtAfter(UUID senderId, Instant since);
}
