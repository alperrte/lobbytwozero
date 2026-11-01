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
}
