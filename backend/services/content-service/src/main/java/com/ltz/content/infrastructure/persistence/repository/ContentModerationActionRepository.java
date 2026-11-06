package com.ltz.content.infrastructure.persistence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ltz.content.domain.entity.ContentModerationAction;

public interface ContentModerationActionRepository extends JpaRepository<ContentModerationAction, UUID> {

	/** İçeriğin moderasyon geçmişi, en yeni karar önce. */
	List<ContentModerationAction> findByContentIdOrderByCreatedAtDescIdDesc(UUID contentId);
}
