package com.ltz.content.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.content.api.dto.request.TranslationRequest;
import com.ltz.content.api.dto.response.ContentResponse;
import com.ltz.content.application.port.CurrentUserPort;
import com.ltz.content.domain.entity.Content;
import com.ltz.content.domain.entity.ContentTranslation;
import com.ltz.content.domain.entity.ContentTranslationId;
import com.ltz.content.domain.exception.ContentNotEditableException;
import com.ltz.content.domain.exception.ContentTranslationNotFoundException;
import com.ltz.content.domain.exception.InvalidContentRequestException;
import com.ltz.content.domain.exception.PublishRequirementsNotMetException;
import com.ltz.content.infrastructure.persistence.repository.ContentRepository;
import com.ltz.content.infrastructure.persistence.repository.ContentTranslationRepository;

/**
 * Çoklu dil (CONT-15): içeriğin Türkçe ve İngilizce çevirileri. İlk dildeki çeviri {@code PATCH /contents/{id}} ile düzenlenir;
 * bu servis diğer dildeki çeviriyi ekler/tamamen değiştirir ya da siler. İlk dildeki çeviri silinemez. Yayımlanmış içerikte
 * çeviri de yayın kurallarını (başlık en az 5, gövde en az 30 karakter) sağlamalıdır.
 */
@Service
public class ContentTranslationService {

	private final ContentRepository contentRepository;
	private final ContentTranslationRepository translationRepository;
	private final ContentAccess contentAccess;
	private final ContentViewAssembler viewAssembler;
	private final CurrentUserPort currentUser;
	private final Clock clock;

	public ContentTranslationService(ContentRepository contentRepository,
			ContentTranslationRepository translationRepository, ContentAccess contentAccess,
			ContentViewAssembler viewAssembler, CurrentUserPort currentUser, Clock clock) {
		this.contentRepository = contentRepository;
		this.translationRepository = translationRepository;
		this.contentAccess = contentAccess;
		this.viewAssembler = viewAssembler;
		this.currentUser = currentUser;
		this.clock = clock;
	}

	@Transactional
	public ContentResponse putTranslation(UUID contentId, String locale, TranslationRequest request) {
		Content content = editableOwned(contentId, locale);
		if (content.getDefaultLocale().equals(locale)) {
			throw new InvalidContentRequestException(
					"The original language is edited with PATCH on the content, not as a translation.");
		}
		String title = request.title().strip();
		if (title.isEmpty()) {
			throw new InvalidContentRequestException("The title must not be blank.");
		}
		String summary = ContentService.clean(request.summary());
		String body = ContentService.clean(request.body());
		if (content.isPublished()) {
			var issues = ContentPublishRules.issuesFor(locale, title, body);
			if (!issues.isEmpty()) {
				throw new PublishRequirementsNotMetException(issues);
			}
		}
		ContentTranslationId id = new ContentTranslationId(contentId, locale);
		ContentTranslation translation = translationRepository.findById(id)
				.orElseGet(() -> ContentTranslation.of(contentId, locale, title, summary, body));
		translation.rewrite(title, summary, body);
		translationRepository.save(translation);
		touch(content);
		return viewAssembler.assemble(content, locale);
	}

	@Transactional
	public ContentResponse deleteTranslation(UUID contentId, String locale) {
		Content content = editableOwned(contentId, locale);
		if (content.getDefaultLocale().equals(locale)) {
			throw new InvalidContentRequestException("The original language translation cannot be deleted.");
		}
		ContentTranslationId id = new ContentTranslationId(contentId, locale);
		if (!translationRepository.existsById(id)) {
			throw new ContentTranslationNotFoundException();
		}
		translationRepository.deleteById(id);
		translationRepository.flush();
		touch(content);
		return viewAssembler.assemble(content, content.getDefaultLocale());
	}

	private Content editableOwned(UUID contentId, String locale) {
		if (!ContentService.SUPPORTED_LOCALES.contains(locale)) {
			throw new InvalidContentRequestException("The locale must be one of: tr, en.");
		}
		Content content = contentAccess.loadOwnedForUpdate(contentId, currentUser.requireUserId());
		if (!content.isEditableByOwner()) {
			throw new ContentNotEditableException();
		}
		return content;
	}

	private void touch(Content content) {
		content.touch(Instant.now(clock));
		contentRepository.save(content);
	}
}
