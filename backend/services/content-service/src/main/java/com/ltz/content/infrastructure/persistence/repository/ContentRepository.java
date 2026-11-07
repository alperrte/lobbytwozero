package com.ltz.content.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ltz.content.domain.entity.Content;
import com.ltz.content.domain.enums.ContentStatus;
import com.ltz.content.domain.enums.ContentType;

import jakarta.persistence.LockModeType;

public interface ContentRepository extends JpaRepository<Content, UUID> {

	/** Silinmiş (soft delete) içerik hiçbir okuma yolunda bulunmaz. */
	Optional<Content> findByIdAndDeletedAtIsNull(UUID id);

	/** Aynı içeriğe eşzamanlı görsel ekleme/silme/silme işlemlerini sıraya sokar. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Content c where c.id = :id and c.deletedAt is null")
	Optional<Content> findActiveByIdForUpdate(@Param("id") UUID id);

	boolean existsByAuthorIdAndTypeAndContentHashAndDeletedAtIsNull(UUID authorId, ContentType type, String contentHash);

	boolean existsByAuthorIdAndTypeAndContentHashAndDeletedAtIsNullAndIdNot(UUID authorId, ContentType type,
			String contentHash, UUID id);

	/** Silinmiş içerikler de sayılır: sil-yeniden-oluştur döngüsüyle saatlik sınır aşılamaz. */
	long countByAuthorIdAndCreatedAtAfter(UUID authorId, Instant since);

	long countByAuthorIdAndStatusAndDeletedAtIsNull(UUID authorId, ContentStatus status);
}
