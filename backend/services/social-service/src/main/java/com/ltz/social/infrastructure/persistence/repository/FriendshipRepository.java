package com.ltz.social.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ltz.social.domain.entity.Friendship;
import com.ltz.social.domain.entity.FriendshipId;

public interface FriendshipRepository extends JpaRepository<Friendship, FriendshipId> {

    /**
     * Kullanıcının arkadaşlıkları, en yeniden eskiye; eşitlikte arkadaşın kimliği azalan. Çift tek satırdır, bu
     * yüzden "arkadaş", satırın kullanıcı olmayan tarafıdır.
     */
    @Query("""
            select f from Friendship f
            where f.id.userLowId = :userId or f.id.userHighId = :userId
            order by f.createdAt desc,
                     (case when f.id.userLowId = :userId then f.id.userHighId else f.id.userLowId end) desc""")
    List<Friendship> findFirstPage(@Param("userId") UUID userId, Limit limit);

    @Query("""
            select f from Friendship f
            where (f.id.userLowId = :userId or f.id.userHighId = :userId)
              and (f.createdAt < :createdAt
                   or (f.createdAt = :createdAt
                       and (case when f.id.userLowId = :userId then f.id.userHighId else f.id.userLowId end)
                           < :friendId))
            order by f.createdAt desc,
                     (case when f.id.userLowId = :userId then f.id.userHighId else f.id.userLowId end) desc""")
    List<Friendship> findPageAfter(@Param("userId") UUID userId, @Param("createdAt") Instant createdAt,
            @Param("friendId") UUID friendId, Limit limit);

    /** Atomik silme: eşzamanlı iki çıkarma isteğinden yalnız biri 1 döner. */
    @Modifying
    @Query("delete from Friendship f where f.id = :id")
    int deleteByFriendshipId(@Param("id") FriendshipId id);
}
