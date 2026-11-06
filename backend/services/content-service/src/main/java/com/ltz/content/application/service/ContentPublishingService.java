package com.ltz.content.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.content.api.dto.response.ContentResponse;
import com.ltz.content.application.port.CurrentUserPort;
import com.ltz.content.domain.entity.Content;
import com.ltz.content.domain.entity.ContentTranslation;
import com.ltz.content.domain.enums.ContentType;
import com.ltz.content.domain.exception.AdministratorRequiredException;
import com.ltz.content.domain.exception.ContentAlreadyPublishedException;
import com.ltz.content.domain.exception.ContentNotEditableException;
import com.ltz.content.domain.exception.ContentNotPublishedException;
import com.ltz.content.domain.exception.OfficialAnnouncementForbiddenException;
import com.ltz.content.domain.exception.PublishRequirementsNotMetException;
import com.ltz.content.infrastructure.persistence.repository.ContentRepository;
import com.ltz.content.infrastructure.persistence.repository.ContentTranslationRepository;

/**
 * Yayınlama (CONT-09) ve öne çıkarma (CONT-11). Kayıtlı her kullanıcı kendi {@code NEWS}/{@code USER_CONTENT} taslağını önceden
 * moderatör onayı olmadan yayınlar; resmî duyuruyu (ANNOUNCEMENT) yalnız ADMIN yayınlar. Yayın kuralları
 * ({@link ContentPublishRules}) içeriğin tüm çevirileri için denetlenir. Öne çıkarma yalnız ADMIN'e aittir ve yalnız yayımlanmış
 * içerik için geçerlidir.
 */
@Service
public class ContentPublishingService {

	private final ContentRepository contentRepository;
	private final ContentTranslationRepository translationRepository;
	private final ContentAccess contentAccess;
	private final ContentViewAssembler viewAssembler;
	private final CurrentUserPort currentUser;
	private final Clock clock;

	public ContentPublishingService(ContentRepository contentRepository, ContentTranslationRepository translationRepository,
			ContentAccess contentAccess, ContentViewAssembler viewAssembler, CurrentUserPort currentUser, Clock clock) {
		this.contentRepository = contentRepository;
		this.translationRepository = translationRepository;
		this.contentAccess = contentAccess;
		this.viewAssembler = viewAssembler;
		this.currentUser = currentUser;
		this.clock = clock;
	}

	@Transactional
	public ContentResponse publish(UUID contentId) {
		Content content = contentAccess.loadOwnedForUpdate(contentId, currentUser.requireUserId());
		if (content.isPublished()) {
			throw new ContentAlreadyPublishedException();
		}
		if (!content.isDraft()) {
			throw new ContentNotEditableException();
		}
		if (content.getType() == ContentType.ANNOUNCEMENT && !currentUser.hasAdminRole()) {
			throw new OfficialAnnouncementForbiddenException();
		}
		requirePublishable(translationRepository.findByIdContentId(contentId));
		content.publish(Instant.now(clock));
		contentRepository.save(content);
		return viewAssembler.assemble(content, content.getDefaultLocale());
	}

	@Transactional
	public ContentResponse feature(UUID contentId) {
		UUID adminId = requireAdmin();
		Content content = contentAccess.loadActiveForUpdate(contentId);
		if (!content.isPublished()) {
			throw new ContentNotPublishedException();
		}
		if (content.feature(adminId, Instant.now(clock))) {
			contentRepository.save(content);
		}
		return viewAssembler.assemble(content, content.getDefaultLocale());
	}

	@Transactional
	public ContentResponse unfeature(UUID contentId) {
		requireAdmin();
		Content content = contentAccess.loadActiveForUpdate(contentId);
		if (content.unfeature(Instant.now(clock))) {
			contentRepository.save(content);
		}
		return viewAssembler.assemble(content, content.getDefaultLocale());
	}

	/** Kimlik doğrulanmış olmalı ve ADMIN rolü taşımalıdır. */
	private UUID requireAdmin() {
		UUID userId = currentUser.requireUserId();
		if (!currentUser.hasAdminRole()) {
			throw new AdministratorRequiredException();
		}
		return userId;
	}

	private static void requirePublishable(List<ContentTranslation> translations) {
		List<String> issues = new ArrayList<>();
		for (ContentTranslation translation : translations) {
			issues.addAll(ContentPublishRules.issuesFor(translation.getId().locale(), translation.getTitle(),
					translation.getBody()));
		}
		if (!issues.isEmpty()) {
			throw new PublishRequirementsNotMetException(issues);
		}
	}
}
