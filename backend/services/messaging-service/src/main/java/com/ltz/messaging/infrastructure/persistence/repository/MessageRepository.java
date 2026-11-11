package com.ltz.messaging.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ltz.messaging.domain.entity.Message;

import jakarta.persistence.LockModeType;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    Optional<Message> findBySenderIdAndClientMessageId(UUID senderId, String clientMessageId);

    /** Mesaj yalnız verilen sohbetteyse döner (başka sohbetin mesajı bulunmaz). */
    Optional<Message> findByIdAndConversationId(UUID id, UUID conversationId);

    /** Herkesten silme (MSG-26): satır kilidiyle; eşzamanlı tepki ve iletme silmeyi bekler. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Message m where m.id = :id and m.conversationId = :conversationId")
    Optional<Message> findByIdAndConversationIdForUpdate(@Param("id") UUID id,
                                                         @Param("conversationId") UUID conversationId);

    /** Tepki ve iletme (MSG-20/23): paylaşımlı kilit; mesaj işlem sürerken herkesten silinemez. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select m from Message m where m.id = :id and m.conversationId = :conversationId")
    Optional<Message> findByIdAndConversationIdForShare(@Param("id") UUID id,
                                                        @Param("conversationId") UUID conversationId);

    /**
     * Geçmiş: {@code beforeSeq}'ten eski, {@code fromSeq} ve sonrası mesajlar, yeniden eskiye; {@code viewer}'ın
     * kendi görünümünden kaldırdıkları (MSG-25) hariç.
     */
    @Query("""
            select m from Message m
            where m.conversationId = :conversationId and m.seq < :beforeSeq and m.seq >= :fromSeq
              and not exists (select 1 from HiddenMessage h where h.id.userId = :viewer and h.id.messageId = m.id)
            order by m.seq desc""")
    List<Message> findOlder(@Param("conversationId") UUID conversationId, @Param("beforeSeq") long beforeSeq,
                            @Param("fromSeq") long fromSeq, @Param("viewer") UUID viewer, Limit limit);

    /**
     * Yeniden bağlanma: {@code afterSeq}'ten yeni mesajlar, eskiden yeniye (kayıpsız eşitleme); {@code viewer}'ın
     * gizledikleri hariç.
     */
    @Query("""
            select m from Message m
            where m.conversationId = :conversationId and m.seq > :afterSeq and m.seq >= :fromSeq
              and not exists (select 1 from HiddenMessage h where h.id.userId = :viewer and h.id.messageId = m.id)
            order by m.seq""")
    List<Message> findNewer(@Param("conversationId") UUID conversationId, @Param("afterSeq") long afterSeq,
                            @Param("fromSeq") long fromSeq, @Param("viewer") UUID viewer, Limit limit);

    /**
     * Sohbet listesi (tek sorgu): kullanıcının her aktif sohbetinde görebildiği ve kendi görünümünden kaldırmadığı son
     * mesaj (MSG-08/25). Sohbet başına tarama, sondan geriye kullanıcının gizlediği ardışık mesaj sayısı kadardır.
     */
    @Query(value = """
            select m.* from conversation_members cm
            cross join lateral (
                select * from messages m2
                where m2.conversation_id = cm.conversation_id and m2.seq >= cm.visible_from_seq
                  and not exists (select 1 from message_hidden_for_users h
                                  where h.user_id = cm.user_id and h.message_id = m2.id)
                order by m2.seq desc
                limit 1) m
            where cm.user_id = :userId and cm.conversation_id in (:conversationIds) and cm.left_at is null""",
            nativeQuery = true)
    List<Message> findLastVisibleMessages(@Param("userId") UUID userId,
                                          @Param("conversationIds") Collection<UUID> conversationIds);

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
