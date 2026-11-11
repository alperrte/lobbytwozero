package com.ltz.messaging.infrastructure.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ltz.messaging.domain.entity.ConversationMember;
import com.ltz.messaging.domain.entity.ConversationMemberId;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, ConversationMemberId> {

    @Query("""
            select m from ConversationMember m
            where m.id.conversationId = :conversationId and m.id.userId = :userId and m.leftAt is null""")
    Optional<ConversationMember> findActive(@Param("conversationId") UUID conversationId,
                                            @Param("userId") UUID userId);

    /** Aktif üyeler, katılma sırasıyla (eşitlikte kullanıcı kimliği); sahip devri de bu sırayı izler. */
    @Query("""
            select m from ConversationMember m
            where m.id.conversationId = :conversationId and m.leftAt is null
            order by m.joinedAt, m.id.userId""")
    List<ConversationMember> findActiveMembers(@Param("conversationId") UUID conversationId);

    /** Eklenecek kullanıcıların bu sohbetteki (aktif ya da eski) üyelik satırları. */
    @Query("""
            select m from ConversationMember m
            where m.id.conversationId = :conversationId and m.id.userId in :userIds""")
    List<ConversationMember> findAllOf(@Param("conversationId") UUID conversationId,
                                       @Param("userIds") Collection<UUID> userIds);

    /** Kullanıcının verilen sohbetlerdeki aktif üyelikleri (sohbet listesi). */
    @Query("""
            select m from ConversationMember m
            where m.id.userId = :userId and m.id.conversationId in :conversationIds and m.leftAt is null""")
    List<ConversationMember> findActiveOf(@Param("userId") UUID userId,
                                          @Param("conversationIds") Collection<UUID> conversationIds);

    /** Verilen sohbetlerin, kullanıcı dışındaki aktif üyeleri (birebir sohbette karşı taraf). */
    @Query("""
            select m from ConversationMember m
            where m.id.conversationId in :conversationIds and m.id.userId <> :userId and m.leftAt is null""")
    List<ConversationMember> findActiveOthers(@Param("conversationIds") Collection<UUID> conversationIds,
                                              @Param("userId") UUID userId);

    /** Verilen sohbetlerin tüm aktif üyeleri, tek sorguda (okundu durumu su seviyesi, MSG-11). */
    @Query("""
            select m from ConversationMember m
            where m.id.conversationId in :conversationIds and m.leftAt is null""")
    List<ConversationMember> findActiveMembersOf(@Param("conversationIds") Collection<UUID> conversationIds);

    /** Sohbet başına aktif üye sayısı: {@code [conversationId, count]}. */
    @Query("""
            select m.id.conversationId, count(m) from ConversationMember m
            where m.id.conversationId in :conversationIds and m.leftAt is null
            group by m.id.conversationId""")
    List<Object[]> countActive(@Param("conversationIds") Collection<UUID> conversationIds);

    /** Okunmamış sayımının üst sınırı: {@code 100} "100 ya da daha fazla" demektir, arayüz {@code 99+} gösterir. */
    int UNREAD_COUNT_CAP = 100;

    /**
     * Sohbet başına okunmamış mesaj sayısı (MSG-13): okuma işaretçisinden sonraki, üyenin görebildiği, başkasının
     * gönderdiği ve üyenin kendi görünümünden kaldırmadığı (MSG-25) mesajlar. {@code [conversationId, count]}. Sayım
     * sohbet başına {@link #UNREAD_COUNT_CAP}'te durur ({@code (conversation_id, seq)} indeksiyle en çok 100 satır
     * okunur): çok okunmamış birikmiş sohbetlerde maliyet sabittir.
     */
    @Query(value = """
            select m.conversation_id,
                   (select count(*) from (
                        select 1 from messages msg
                        where msg.conversation_id = m.conversation_id and msg.seq > m.last_read_seq
                          and msg.seq >= m.visible_from_seq and msg.sender_id <> :userId
                          and not exists (select 1 from message_hidden_for_users h
                                          where h.user_id = :userId and h.message_id = msg.id)
                        order by msg.seq
                        limit 100) unread)
            from conversation_members m
            where m.user_id = :userId and m.conversation_id in (:conversationIds) and m.left_at is null""",
            nativeQuery = true)
    List<Object[]> countUnread(@Param("userId") UUID userId,
                               @Param("conversationIds") Collection<UUID> conversationIds);
}
