package com.ltz.messaging.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ltz.messaging.domain.entity.HiddenMessage;
import com.ltz.messaging.domain.entity.HiddenMessageId;

public interface HiddenMessageRepository extends JpaRepository<HiddenMessage, HiddenMessageId> {

    /** Verilen mesajlardan kullanıcının kendi görünümünden kaldırdıkları (tek sorgu). */
    @Query("select h.id.messageId from HiddenMessage h where h.id.userId = :userId and h.id.messageId in :messageIds")
    Set<UUID> findHiddenAmong(@Param("userId") UUID userId, @Param("messageIds") Collection<UUID> messageIds);

    /** Benden sil (MSG-25): idempotent ekleme; eklenen satır sayısını (0 ya da 1) döner. */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into message_hidden_for_users (user_id, message_id, hidden_at) values (:userId, :messageId, :at)
            on conflict (user_id, message_id) do nothing""", nativeQuery = true)
    int hide(@Param("userId") UUID userId, @Param("messageId") UUID messageId, @Param("at") Instant at);

    /** Mesajı kendi görünümünden kaldırmış kullanıcılar (canlı olay alıcılarını ve alıntıyı kırpmak için). */
    @Query("select h.id.userId from HiddenMessage h where h.id.messageId = :messageId")
    Set<UUID> findUsersWhoHid(@Param("messageId") UUID messageId);
}
