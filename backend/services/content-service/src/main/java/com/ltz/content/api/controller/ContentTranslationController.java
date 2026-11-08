package com.ltz.content.api.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.content.api.dto.request.TranslationRequest;
import com.ltz.content.api.dto.response.ContentResponse;
import com.ltz.content.application.service.ContentTranslationService;

import jakarta.validation.Valid;

/** Çoklu dil (CONT-15): içeriğin diğer dildeki çevirisini ekler/değiştirir/siler. */
@RestController
@RequestMapping("/api/v1/content/contents/{contentId}/translations/{locale}")
public class ContentTranslationController {

	private final ContentTranslationService translationService;

	public ContentTranslationController(ContentTranslationService translationService) {
		this.translationService = translationService;
	}

	@PutMapping
	public ContentResponse putTranslation(@PathVariable UUID contentId, @PathVariable String locale,
			@Valid @RequestBody TranslationRequest request) {
		return translationService.putTranslation(contentId, locale, request);
	}

	@DeleteMapping
	public ContentResponse deleteTranslation(@PathVariable UUID contentId, @PathVariable String locale) {
		return translationService.deleteTranslation(contentId, locale);
	}
}
