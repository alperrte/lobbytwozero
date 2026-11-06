package com.ltz.content.application.mapper;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ltz.content.api.dto.response.CategoryResponse;
import com.ltz.content.api.dto.response.ContentCardResponse;
import com.ltz.content.api.dto.response.ContentImageResponse;
import com.ltz.content.api.dto.response.ContentResponse;
import com.ltz.content.api.dto.response.ContentSourceResponse;
import com.ltz.content.application.port.ContentFeedPort.Card;
import com.ltz.content.domain.entity.Content;
import com.ltz.content.domain.entity.ContentCategory;
import com.ltz.content.domain.entity.ContentImage;
import com.ltz.content.domain.entity.ContentSource;
import com.ltz.content.domain.entity.ContentTranslation;
import com.ltz.content.infrastructure.persistence.repository.ContentImageMeta;

@Component
public class ContentMapper {

	public ContentResponse toResponse(Content content, ContentTranslation translation, List<ContentImageMeta> images,
			List<String> categories, List<ContentSource> sources, List<String> availableLocales) {
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
				content.getUpdatedAt(),
				content.getGameId(),
				categories,
				sources.stream().map(source -> new ContentSourceResponse(source.getTitle(), source.getUrl(),
						source.getSourceDate())).toList(),
				content.isFeatured(),
				content.getPublishedAt(),
				availableLocales,
				content.getRemovedAt(),
				content.getRemovedReason());
	}

	public ContentCardResponse toCard(Card card, List<String> categories, UUID coverImageId) {
		return new ContentCardResponse(
				card.id(),
				card.authorId(),
				card.type(),
				card.locale(),
				card.title(),
				card.summary(),
				card.gameId(),
				categories,
				coverImageId == null ? null : imageUrl(card.id(), coverImageId),
				card.featured(),
				card.publishedAt());
	}

	public CategoryResponse toResponse(ContentCategory category) {
		return new CategoryResponse(category.getCode(), category.getKind(), category.getNameTr(), category.getNameEn());
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

	/** Görsel baytlarının adresi (gateway biçimi); yayımlanmış içeriğin görseli herkese açıktır. */
	private static String imageUrl(UUID contentId, UUID imageId) {
		return "/api/v1/content/contents/" + contentId + "/images/" + imageId;
	}
}
