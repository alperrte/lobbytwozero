package com.ltz.content.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.content.api.dto.response.ContentImageContent;
import com.ltz.content.api.dto.response.ContentImageResponse;
import com.ltz.content.application.mapper.ContentMapper;
import com.ltz.content.application.port.CurrentUserPort;
import com.ltz.content.application.service.ImageSanitizer.SanitizedImage;
import com.ltz.content.domain.entity.Content;
import com.ltz.content.domain.entity.ContentImage;
import com.ltz.content.domain.exception.ContentImageLimitExceededException;
import com.ltz.content.domain.exception.ContentImageNotFoundException;
import com.ltz.content.domain.exception.ContentNotEditableException;
import com.ltz.content.infrastructure.persistence.repository.ContentImageRepository;

/**
 * İçerik görselleri (CONT-14). Ekleme ve silme yalnız sahibine ve yalnız taslak ya da yayımlanmış içeriğe açıktır; okuma,
 * yayımlanmış içerik için herkese (misafir dahil) açıktır, taslak görseline yalnız sahibi erişir (başkası için 404). Görsel
 * baytları ayrı bir media-service yerine bu servisin veritabanında tutulur.
 */
@Service
public class ContentImageService {

	private final ContentImageRepository imageRepository;
	private final ContentAccess contentAccess;
	private final ImageSanitizer imageSanitizer;
	private final ContentMapper contentMapper;
	private final CurrentUserPort currentUser;
	private final Clock clock;

	public ContentImageService(ContentImageRepository imageRepository, ContentAccess contentAccess,
			ImageSanitizer imageSanitizer, ContentMapper contentMapper, CurrentUserPort currentUser, Clock clock) {
		this.imageRepository = imageRepository;
		this.contentAccess = contentAccess;
		this.imageSanitizer = imageSanitizer;
		this.contentMapper = contentMapper;
		this.currentUser = currentUser;
		this.clock = clock;
	}

	@Transactional
	public ContentImageResponse addImage(UUID contentId, byte[] upload) {
		Content content = contentAccess.loadOwnedForUpdate(contentId, currentUser.requireUserId());
		if (!content.isEditableByOwner()) {
			throw new ContentNotEditableException();
		}
		List<Integer> usedSlots = imageRepository.findSortOrdersByContentId(contentId);
		if (usedSlots.size() >= ContentImage.MAX_IMAGES_PER_CONTENT) {
			throw new ContentImageLimitExceededException(ContentImage.MAX_IMAGES_PER_CONTENT);
		}
		SanitizedImage image = imageSanitizer.sanitize(upload);
		ContentImage saved = imageRepository.save(ContentImage.attach(contentId, image.content(), image.mimeType(),
				image.width(), image.height(), firstFreeSlot(usedSlots), Instant.now(clock)));
		return contentMapper.toResponse(saved);
	}

	@Transactional(readOnly = true)
	public ContentImageContent getImage(UUID contentId, UUID imageId) {
		contentAccess.loadViewable(contentId, currentUser.findUserId().orElse(null));
		ContentImage image = imageRepository.findByIdAndContentId(imageId, contentId)
				.orElseThrow(ContentImageNotFoundException::new);
		return new ContentImageContent(image.getContent(), image.getMimeType());
	}

	@Transactional
	public void deleteImage(UUID contentId, UUID imageId) {
		Content content = contentAccess.loadOwnedForUpdate(contentId, currentUser.requireUserId());
		if (!content.isEditableByOwner()) {
			throw new ContentNotEditableException();
		}
		ContentImage image = imageRepository.findByIdAndContentId(imageId, contentId)
				.orElseThrow(ContentImageNotFoundException::new);
		imageRepository.delete(image);
	}

	private static int firstFreeSlot(List<Integer> usedSlots) {
		Set<Integer> used = new HashSet<>(usedSlots);
		int slot = 0;
		while (used.contains(slot)) {
			slot++;
		}
		return slot;
	}
}
