package com.ltz.content.domain.entity;

import java.util.UUID;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** İçeriğin bir kategoriye bağlanması. Bir içerik en çok {@link #MAX_PER_CONTENT} kategoriye bağlanır. */
@Entity
@Table(name = "content_category_assignments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentCategoryAssignment {

	public static final int MAX_PER_CONTENT = 5;

	@EmbeddedId
	private ContentCategoryAssignmentId id;

	public static ContentCategoryAssignment of(UUID contentId, UUID categoryId) {
		ContentCategoryAssignment assignment = new ContentCategoryAssignment();
		assignment.id = new ContentCategoryAssignmentId(contentId, categoryId);
		return assignment;
	}
}
