package com.ltz.community.infrastructure.persistence.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.ltz.community.domain.entity.Forum;
import com.ltz.community.domain.enums.ForumStatus;

public interface ForumRepository extends JpaRepository<Forum, UUID>, JpaSpecificationExecutor<Forum> {

	boolean existsBySlug(String slug);

	boolean existsByIdAndStatus(UUID id, ForumStatus status);
}
