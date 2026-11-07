package com.ltz.content.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ltz.content.domain.entity.ContentImage;

public interface ContentImageRepository extends JpaRepository<ContentImage, UUID> {

	Optional<ContentImage> findByIdAndContentId(UUID id, UUID contentId);

	/** İçeriğin görsel bilgisini baytlar olmadan, sıra numarasına göre getirir. */
	List<ContentImageMeta> findByContentIdOrderBySortOrderAsc(UUID contentId);

	/** Görsel baytlarını yüklemeden yalnız dolu sıra numaralarını döndürür. */
	@Query("select i.sortOrder from ContentImage i where i.contentId = :contentId")
	List<Integer> findSortOrdersByContentId(@Param("contentId") UUID contentId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from ContentImage i where i.contentId = :contentId")
	int deleteByContentId(@Param("contentId") UUID contentId);
}
