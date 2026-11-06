package com.ltz.content.infrastructure.persistence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ltz.content.domain.entity.ContentSource;

public interface ContentSourceRepository extends JpaRepository<ContentSource, UUID> {

	List<ContentSource> findByContentIdOrderBySortOrderAsc(UUID contentId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from ContentSource s where s.contentId = :contentId")
	int deleteByContentId(@Param("contentId") UUID contentId);
}
