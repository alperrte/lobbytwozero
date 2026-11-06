package com.ltz.content.infrastructure.persistence.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ltz.content.domain.entity.ContentTranslation;
import com.ltz.content.domain.entity.ContentTranslationId;

public interface ContentTranslationRepository extends JpaRepository<ContentTranslation, ContentTranslationId> {

	/** İçeriğin tüm dillerdeki metnini kalıcı olarak siler (silinen içerik için saklama süresi 0 gün). */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from ContentTranslation t where t.id.contentId = :contentId")
	int deleteByContentId(@Param("contentId") UUID contentId);
}
