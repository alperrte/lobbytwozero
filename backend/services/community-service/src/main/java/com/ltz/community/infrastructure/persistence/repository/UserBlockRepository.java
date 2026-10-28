package com.ltz.community.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ltz.community.domain.entity.UserBlock;
import com.ltz.community.domain.entity.UserBlockId;

public interface UserBlockRepository extends JpaRepository<UserBlock, UserBlockId> {

	/** Engellemeyi ekler; zaten varsa hiçbir şey yapmaz (olay yeniden teslimine dayanıklı). */
	@Modifying
	@Query(value = """
			insert into user_blocks (blocker_id, blocked_id, created_at)
			values (:blockerId, :blockedId, :createdAt)
			on conflict (blocker_id, blocked_id) do nothing
			""", nativeQuery = true)
	int insertIfAbsent(@Param("blockerId") UUID blockerId, @Param("blockedId") UUID blockedId,
			@Param("createdAt") Instant createdAt);

	@Modifying
	@Query("delete from UserBlock b where b.id.blockerId = :blockerId and b.id.blockedId = :blockedId")
	int deleteBlock(@Param("blockerId") UUID blockerId, @Param("blockedId") UUID blockedId);

	/** İki kullanıcı arasında (hangi yönde olursa olsun) engelleme var mı. */
	@Query("""
			select count(b) > 0 from UserBlock b
			where (b.id.blockerId = :first and b.id.blockedId = :second)
			   or (b.id.blockerId = :second and b.id.blockedId = :first)
			""")
	boolean existsBetween(@Param("first") UUID first, @Param("second") UUID second);

	@Query("select b.id.blockedId from UserBlock b where b.id.blockerId = :userId")
	List<UUID> findBlockedIds(@Param("userId") UUID userId);

	@Query("select b.id.blockerId from UserBlock b where b.id.blockedId = :userId")
	List<UUID> findBlockerIds(@Param("userId") UUID userId);
}
