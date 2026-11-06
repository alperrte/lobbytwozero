package com.ltz.content.infrastructure.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ltz.content.domain.entity.ContentCategory;

public interface ContentCategoryRepository extends JpaRepository<ContentCategory, UUID> {

	List<ContentCategory> findAllByOrderBySortOrderAsc();

	List<ContentCategory> findByCodeIn(Collection<String> codes);
}
