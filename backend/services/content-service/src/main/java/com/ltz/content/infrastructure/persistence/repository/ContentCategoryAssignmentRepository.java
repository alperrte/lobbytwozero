package com.ltz.content.infrastructure.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ltz.content.domain.entity.ContentCategoryAssignment;
import com.ltz.content.domain.entity.ContentCategoryAssignmentId;

public interface ContentCategoryAssignmentRepository
		extends JpaRepository<ContentCategoryAssignment, ContentCategoryAssignmentId> {

	/** Verilen içeriklerin kategori kodlarını, kategori sırasına göre tek sorguda getirir (N+1 yok). */
	@Query("select a.id.contentId as contentId, c.code as code from ContentCategoryAssignment a "
			+ "join ContentCategory c on c.id = a.id.categoryId where a.id.contentId in :contentIds order by c.sortOrder")
	List<CategoryCode> findCodesByContentIds(@Param("contentIds") Collection<UUID> contentIds);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from ContentCategoryAssignment a where a.id.contentId = :contentId")
	int deleteByContentId(@Param("contentId") UUID contentId);
}
