package com.ltz.community.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ltz.community.domain.entity.PostLike;
import com.ltz.community.domain.entity.PostLikeId;

public interface PostLikeRepository extends JpaRepository<PostLike, PostLikeId> {

	/** Beğeniyi ekler; zaten varsa hiçbir şey yapmaz. Eklenen satır sayısını (0 veya 1) döndürür. */
	@Modifying
	@Query(value = """
			insert into post_likes (post_id, user_id, created_at)
			values (:postId, :userId, :createdAt)
			on conflict (post_id, user_id) do nothing
			""", nativeQuery = true)
	int insertIfAbsent(@Param("postId") UUID postId, @Param("userId") UUID userId,
			@Param("createdAt") Instant createdAt);

	/** Beğeniyi kaldırır; yoksa 0 döner. */
	@Modifying
	@Query("delete from PostLike l where l.id.postId = :postId and l.id.userId = :userId")
	int deleteLike(@Param("postId") UUID postId, @Param("userId") UUID userId);

	/** Kullanıcının verilen gönderilerden beğendiklerinin kimlikleri (sayfa başına tek sorgu). */
	@Query("select l.id.postId from PostLike l where l.id.userId = :userId and l.id.postId in :postIds")
	List<UUID> findLikedPostIds(@Param("userId") UUID userId, @Param("postIds") Collection<UUID> postIds);
}
