package com.ltz.messaging.infrastructure.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /** Sohbet başına aktif üye sayısı: {@code [conversationId, count]}. */
    @Query("""
            select m.id.conversationId, count(m) from ConversationMember m
            where m.id.conversationId in :conversationIds and m.leftAt is null
            group by m.id.conversationId""")
    List<Object[]> countActive(@Param("conversationIds") Collection<UUID> conversationIds);

    /**
     * Sohbet başına okunmamış mesaj sayısı (MSG-13): okuma işaretçisinden sonraki, üyenin görebildiği ve başkasının
     * gönderdiği mesajlar. {@code [conversationId, count]}; okunmamışı olmayan sohbet listede yoktur.
     */
    @Query("""
            select m.id.conversationId, count(msg) from ConversationMember m, Message msg
            where m.id.userId = :userId and m.id.conversationId in :conversationIds and m.leftAt is null
              and msg.conversationId = m.id.conversationId and msg.seq > m.lastReadSeq
              and msg.seq >= m.visibleFromSeq and msg.senderId <> :userId
            group by m.id.conversationId""")
    List<Object[]> countUnread(@Param("userId") UUID userId,
                               @Param("conversationIds") Collection<UUID> conversationIds);

    /** Okuma işaretçisini yalnız ileri taşır (eşzamanlı isteklerde geri gitmez). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ConversationMember m set m.lastReadSeq = :seq
            where m.id.conversationId = :conversationId and m.id.userId = :userId and m.leftAt is null
              and m.lastReadSeq < :seq""")
    int advanceLastRead(@Param("conversationId") UUID conversationId, @Param("userId") UUID userId,
                        @Param("seq") long seq);
}
