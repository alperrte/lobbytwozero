package com.ltz.social.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ltz.social.domain.entity.UserBlock;
import com.ltz.social.domain.entity.UserBlockId;

public interface UserBlockRepository extends JpaRepository<UserBlock, UserBlockId> {

    /** İki kullanıcı arasında, herhangi bir yönde engel var mı. */
    @Query("""
            select count(b) > 0 from UserBlock b
            where (b.id.blockerId = :first and b.id.blockedId = :second)
               or (b.id.blockerId = :second and b.id.blockedId = :first)""")
    boolean existsBetween(@Param("first") UUID first, @Param("second") UUID second);

    /** {@code userId}'nin engellediği hesaplardan {@code others} içinde olanlar (engel sorgusu, yön 1). */
    @Query("select b.id.blockedId from UserBlock b where b.id.blockerId = :userId and b.id.blockedId in :others")
    List<UUID> findBlockedAmong(@Param("userId") UUID userId, @Param("others") Collection<UUID> others);

    /** {@code userId}'yi engelleyen hesaplardan {@code others} içinde olanlar (engel sorgusu, yön 2). */
    @Query("select b.id.blockerId from UserBlock b where b.id.blockedId = :userId and b.id.blockerId in :others")
    List<UUID> findBlockersAmong(@Param("userId") UUID userId, @Param("others") Collection<UUID> others);

    /** Kullanıcının engellediği hesaplar, en yeniden eskiye; eşitlikte engellenen kimliği azalan. */
    @Query("""
            select b from UserBlock b
            where b.id.blockerId = :blockerId
            order by b.createdAt desc, b.id.blockedId desc""")
    List<UserBlock> findFirstPage(@Param("blockerId") UUID blockerId, Limit limit);

    @Query("""
            select b from UserBlock b
            where b.id.blockerId = :blockerId
              and (b.createdAt < :createdAt or (b.createdAt = :createdAt and b.id.blockedId < :blockedId))
            order by b.createdAt desc, b.id.blockedId desc""")
    List<UserBlock> findPageAfter(@Param("blockerId") UUID blockerId, @Param("createdAt") Instant createdAt,
            @Param("blockedId") UUID blockedId, Limit limit);

    /** Hesap silme: kullanıcının koyduğu ve kendisine konan tüm engeller. */
    @Modifying
    @Query("delete from UserBlock b where b.id.blockerId = :accountId or b.id.blockedId = :accountId")
    int deleteAllOf(@Param("accountId") UUID accountId);

    /** Atomik silme: eşzamanlı iki kaldırma isteğinden yalnız biri 1 döner. */
    @Modifying
    @Query("delete from UserBlock b where b.id = :id")
    int deleteByBlockId(@Param("id") UserBlockId id);
}
