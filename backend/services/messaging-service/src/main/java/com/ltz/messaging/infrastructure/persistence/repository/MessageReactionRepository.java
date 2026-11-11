package com.ltz.messaging.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ltz.messaging.domain.entity.MessageReaction;
import com.ltz.messaging.domain.entity.MessageReactionId;

public interface MessageReactionRepository extends JpaRepository<MessageReaction, MessageReactionId> {

    /** Sayfadaki mesajların tepki özeti (tek sorgu): {@code [messageId, emoji, count, min(reactedAt)]}. */
    @Query("""
            select r.id.messageId, r.emoji, count(r), min(r.reactedAt) from MessageReaction r
            where r.id.messageId in :messageIds
            group by r.id.messageId, r.emoji""")
    List<Object[]> summarize(@Param("messageIds") Collection<UUID> messageIds);

    /** Tepki ver ya da değiştir: kişi başına tek satır (yarışta da). */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into message_reactions (message_id, user_id, emoji, reacted_at)
            values (:messageId, :userId, :emoji, :at)
            on conflict (message_id, user_id) do update set emoji = excluded.emoji, reacted_at = excluded.reacted_at""",
            nativeQuery = true)
    int upsert(@Param("messageId") UUID messageId, @Param("userId") UUID userId, @Param("emoji") String emoji,
               @Param("at") Instant at);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from MessageReaction r where r.id.messageId = :messageId and r.id.userId = :userId")
    int deleteByMessageIdAndUserId(@Param("messageId") UUID messageId, @Param("userId") UUID userId);

    /** Mesajın tepkileri, yeniden eskiye (grup üye sınırıyla sınırlı). */
    @Query("select r from MessageReaction r where r.id.messageId = :messageId order by r.reactedAt desc")
    List<MessageReaction> findNewestFirst(@Param("messageId") UUID messageId);

    /** Herkesten silmede (MSG-26) mesajın tüm tepkileri silinir. */
    @Modifying(flushAutomatically = true)
    @Query("delete from MessageReaction r where r.id.messageId = :messageId")
    int deleteByMessageId(@Param("messageId") UUID messageId);

    /** Kullanıcının sayfadaki kendi tepkileri: {@code [messageId, emoji]}. */
    @Query("""
            select r.id.messageId, r.emoji from MessageReaction r
            where r.id.userId = :userId and r.id.messageId in :messageIds""")
    List<Object[]> findMine(@Param("userId") UUID userId, @Param("messageIds") Collection<UUID> messageIds);
}
