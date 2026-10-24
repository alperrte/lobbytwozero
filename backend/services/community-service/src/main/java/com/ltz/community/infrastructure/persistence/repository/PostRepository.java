package com.ltz.community.infrastructure.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ltz.community.domain.entity.Post;
import com.ltz.community.domain.enums.PostStatus;

import jakarta.persistence.LockModeType;

public interface PostRepository extends JpaRepository<Post, UUID>, JpaSpecificationExecutor<Post> {

	/** Aynı gönderiye eşzamanlı görsel ekleme/silme işlemlerini sıraya sokar. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Post p where p.id = :id")
	Optional<Post> findByIdForUpdate(@Param("id") UUID id);

	/**
	 * Sayaç güncellemeleri tek SQL ifadesiyle atomik yapılır (oku-değiştir-yaz yarışı yok). Artırma yalnız herkese
	 * açık (VISIBLE) gönderide çalışır; etkilenen satır sayısı 0 ise gönderi artık etkileşime açık değildir.
	 * Toplu güncelleme kalıcılık bağlamını atlar; bu yüzden bağlam temizlenir.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Post p set p.likeCount = p.likeCount + 1 where p.id = :id and p.status = com.ltz.community.domain.enums.PostStatus.VISIBLE")
	int incrementLikeCount(@Param("id") UUID id);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Post p set p.likeCount = p.likeCount - 1 where p.id = :id and p.likeCount > 0")
	int decrementLikeCount(@Param("id") UUID id);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Post p set p.commentCount = p.commentCount + 1 where p.id = :id and p.status = com.ltz.community.domain.enums.PostStatus.VISIBLE")
	int incrementCommentCount(@Param("id") UUID id);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Post p set p.commentCount = p.commentCount - 1 where p.id = :id and p.commentCount > 0")
	int decrementCommentCount(@Param("id") UUID id);

	/** Moderasyonla geri açılan yorum için sayaç artışı; gönderi durumundan bağımsızdır (sayaç VISIBLE yorum sayısıdır). */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Post p set p.commentCount = p.commentCount + 1 where p.id = :id")
	int restoreCommentCount(@Param("id") UUID id);

	/** Kullanıcının bu orijinali silinmemiş bir repost ile zaten paylaşıp paylaşmadığı. */
	boolean existsByAuthorIdAndRepostOfIdAndStatusNot(UUID authorId, UUID repostOfId, PostStatus status);

	@Query("select p.likeCount from Post p where p.id = :id")
	Optional<Integer> findLikeCountById(@Param("id") UUID id);
}
