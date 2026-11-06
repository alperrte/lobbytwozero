package com.ltz.content.application.mapper;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ltz.content.api.dto.response.ContentImageResponse;
import com.ltz.content.api.dto.response.ContentResponse;
import com.ltz.content.domain.entity.Content;
import com.ltz.content.domain.entity.ContentImage;
import com.ltz.content.domain.entity.ContentTranslation;
import com.ltz.content.infrastructure.persistence.repository.ContentImageMeta;

@Component
public class ContentMapper {

	public ContentResponse toResponse(Content content, ContentTranslation translation, List<ContentImageMeta> images) {
		return new ContentResponse(
				content.getId(),
				content.getAuthorId(),
				content.getType(),
				content.getStatus(),
				translation.getId().locale(),
				translation.getTitle(),
				translation.getSummary(),
				translation.getBody(),
				images.stream().map(this::toResponse).toList(),
				content.getCreatedAt(),
				content.getUpdatedAt());
	}

	public ContentImageResponse toResponse(ContentImage image) {
		return new ContentImageResponse(
				image.getId(),
				image.getContentId(),
				image.getMimeType(),
				image.getSizeBytes(),
				image.getWidth(),
				image.getHeight(),
				image.getSortOrder(),
				imageUrl(image.getContentId(), image.getId()),
				image.getCreatedAt());
	}

	private ContentImageResponse toResponse(ContentImageMeta image) {
		return new ContentImageResponse(
				image.getId(),
				image.getContentId(),
				image.getMimeType(),
				image.getSizeBytes(),
				image.getWidth(),
				image.getHeight(),
				image.getSortOrder(),
				imageUrl(image.getContentId(), image.getId()),
				image.getCreatedAt());
	}

	/** Görsel baytlarının yetki kontrollü adresi (gateway biçimi). */
	private static String imageUrl(UUID contentId, UUID imageId) {
		return "/api/v1/content/contents/" + contentId + "/images/" + imageId;
	}
}
