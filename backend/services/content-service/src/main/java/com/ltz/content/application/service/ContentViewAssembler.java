package com.ltz.content.application.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ltz.content.api.dto.response.ContentResponse;
import com.ltz.content.application.mapper.ContentMapper;
import com.ltz.content.domain.entity.Content;
import com.ltz.content.domain.entity.ContentTranslation;
import com.ltz.content.infrastructure.persistence.repository.CategoryCode;
import com.ltz.content.infrastructure.persistence.repository.ContentCategoryAssignmentRepository;
import com.ltz.content.infrastructure.persistence.repository.ContentImageRepository;
import com.ltz.content.infrastructure.persistence.repository.ContentSourceRepository;
import com.ltz.content.infrastructure.persistence.repository.ContentTranslationRepository;

/**
 * Bir içeriğin tam görünümünü toplar: çeviriler (istenen dil, yoksa içeriğin ilk dili), görsel bilgisi (baytlar olmadan),
 * kategori kodları ve kaynaklar. Her çağrı sabit sayıda sorgu yapar.
 */
@Component
public class ContentViewAssembler {

	private final ContentTranslationRepository translationRepository;
	private final ContentImageRepository imageRepository;
	private final ContentCategoryAssignmentRepository assignmentRepository;
	private final ContentSourceRepository sourceRepository;
	private final ContentMapper contentMapper;

	public ContentViewAssembler(ContentTranslationRepository translationRepository, ContentImageRepository imageRepository,
			ContentCategoryAssignmentRepository assignmentRepository, ContentSourceRepository sourceRepository,
			ContentMapper contentMapper) {
		this.translationRepository = translationRepository;
		this.imageRepository = imageRepository;
		this.assignmentRepository = assignmentRepository;
		this.sourceRepository = sourceRepository;
		this.contentMapper = contentMapper;
	}

	/** {@code preferredLocale} null ise ya da o dilde çeviri yoksa içeriğin ilk dilindeki çeviri sunulur. */
	public ContentResponse assemble(Content content, String preferredLocale) {
		List<ContentTranslation> translations = translationRepository.findByIdContentId(content.getId());
		ContentTranslation shown = pick(content, translations, preferredLocale);
		List<String> categories = assignmentRepository.findCodesByContentIds(List.of(content.getId())).stream()
				.map(CategoryCode::getCode).toList();
		return contentMapper.toResponse(content, shown,
				imageRepository.findByContentIdOrderBySortOrderAsc(content.getId()), categories,
				sourceRepository.findByContentIdOrderBySortOrderAsc(content.getId()),
				availableLocales(content, translations));
	}

	/** İlk dildeki çeviri her içerikte bulunmak zorundadır; yoksa veri tutarsızdır (500). */
	public ContentTranslation defaultTranslation(Content content, List<ContentTranslation> translations) {
		return translations.stream()
				.filter(translation -> translation.getId().locale().equals(content.getDefaultLocale()))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("Content has no translation: " + content.getId()));
	}

	private ContentTranslation pick(Content content, List<ContentTranslation> translations, String preferredLocale) {
		if (preferredLocale != null) {
			for (ContentTranslation translation : translations) {
				if (translation.getId().locale().equals(preferredLocale)) {
					return translation;
				}
			}
		}
		return defaultTranslation(content, translations);
	}

	private static List<String> availableLocales(Content content, List<ContentTranslation> translations) {
		List<String> locales = new ArrayList<>(translations.stream().map(translation -> translation.getId().locale())
				.sorted(Comparator.naturalOrder()).toList());
		// İlk dil her zaman başta gelir.
		locales.remove(content.getDefaultLocale());
		locales.addFirst(content.getDefaultLocale());
		return locales;
	}
}
