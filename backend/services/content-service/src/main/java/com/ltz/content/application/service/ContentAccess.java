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
 * İçeriğe erişim kuralları: silinmiş içerik herkes için yok sayılır. Sahibi olmayan kullanıcıya (misafir dahil) yayımlanmamış
 * (taslak, kaldırılmış, arşivlenmiş) içeriğin varlığı sızdırılmaz (404); yayımlanmış içerikte başkasının içeriğini değiştirme
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

	/** Eşzamanlı işlemleri sıraya sokmak için satır kilidiyle yükler. */
	public Content loadOwnedForUpdate(UUID contentId, UUID userId) {
		return requireOwned(contentRepository.findActiveByIdForUpdate(contentId), userId);
	}

	/**
	 * Yayımlanmış içeriği herkes (misafir dahil), diğer durumları yalnız sahibi görebilir. {@code viewerId} misafirde null'dır.
	 */
	public Content loadViewable(UUID contentId, UUID viewerId) {
		Content content = contentRepository.findByIdAndDeletedAtIsNull(contentId)
				.orElseThrow(ContentNotFoundException::new);
		boolean visible = content.getStatus() == ContentStatus.PUBLISHED || content.isOwnedBy(viewerId);
		if (!visible) {
			throw new ContentNotFoundException();
		}
		return content;
	}

	/** Silinmemiş içeriği sahiplik aramadan yükler (yönetici işlemleri için); yoksa 404. */
	public Content loadActiveForUpdate(UUID contentId) {
		return contentRepository.findActiveByIdForUpdate(contentId).orElseThrow(ContentNotFoundException::new);
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
