package com.ltz.community.infrastructure.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ltz.community.domain.entity.PostImage;

public interface PostImageRepository extends JpaRepository<PostImage, UUID> {

	Optional<PostImage> findByIdAndPostId(UUID id, UUID postId);

	/** Akış sayfasındaki tüm gönderilerin görsel bilgisini tek sorguda (içerik baytı olmadan) getirir. */
	List<PostImageMeta> findByPostIdInOrderBySortOrderAsc(Collection<UUID> postIds);

	/** Görsel baytlarını yüklemeden yalnız dolu sıra numaralarını döndürür. */
	@Query("select i.sortOrder from PostImage i where i.postId = :postId")
	List<Integer> findSortOrdersByPostId(@Param("postId") UUID postId);

	@Modifying
	@Query("delete from PostImage i where i.postId = :postId")
	int deleteByPostId(@Param("postId") UUID postId);
}
