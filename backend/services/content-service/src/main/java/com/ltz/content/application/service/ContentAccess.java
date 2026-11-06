package com.ltz.content.application.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ltz.content.domain.entity.Content;
import com.ltz.content.domain.enums.ContentStatus;
import com.ltz.content.domain.exception.ContentAccessDeniedException;
import com.ltz.content.domain.exception.ContentNotFoundException;
import com.ltz.content.infrastructure.persistence.repository.ContentRepository;

/**
 * İçeriğe erişim kuralları: silinmiş içerik herkes için yok sayılır. Sahibi olmayan kullanıcıya yayımlanmamış (taslak,
 * kaldırılmış, arşivlenmiş) içeriğin varlığı sızdırılmaz (404); yayımlanmış içerikte başkasının içeriğini değiştirme
 * girişimi 403'tür. Her zaman sunucu tarafında, doğrulanmış kullanıcı kimliğiyle denetlenir (IDOR).
 */
@Component
public class ContentAccess {

	private final ContentRepository contentRepository;

	public ContentAccess(ContentRepository contentRepository) {
		this.contentRepository = contentRepository;
	}

	public Content loadOwned(UUID contentId, UUID userId) {
		return requireOwned(contentRepository.findByIdAndDeletedAtIsNull(contentId), userId);
	}

	/** Eşzamanlı görsel işlemlerini sıraya sokmak için satır kilidiyle yükler. */
	public Content loadOwnedForUpdate(UUID contentId, UUID userId) {
		return requireOwned(contentRepository.findActiveByIdForUpdate(contentId), userId);
	}

	/** Sahibi her durumda, diğer kullanıcılar yalnız yayımlanmış içeriği görebilir. */
	public Content loadViewable(UUID contentId, UUID userId) {
		Content content = contentRepository.findByIdAndDeletedAtIsNull(contentId)
				.orElseThrow(ContentNotFoundException::new);
		if (!content.isOwnedBy(userId) && content.getStatus() != ContentStatus.PUBLISHED) {
			throw new ContentNotFoundException();
		}
		return content;
	}

	private Content requireOwned(Optional<Content> found, UUID userId) {
		Content content = found.orElseThrow(ContentNotFoundException::new);
		if (content.isOwnedBy(userId)) {
			return content;
		}
		if (content.getStatus() != ContentStatus.PUBLISHED) {
			throw new ContentNotFoundException();
		}
		throw new ContentAccessDeniedException();
	}
}
