package com.ltz.content.api.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.content.api.dto.request.ContentFeedRequest;
import com.ltz.content.api.dto.request.CreateContentRequest;
import com.ltz.content.api.dto.request.UpdateContentRequest;
import com.ltz.content.api.dto.response.ContentPageResponse;
import com.ltz.content.api.dto.response.ContentResponse;
import com.ltz.content.application.service.ContentFeedService;
import com.ltz.content.application.service.ContentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/content/contents")
public class ContentController {

	private final ContentService contentService;
	private final ContentFeedService feedService;

	public ContentController(ContentService contentService, ContentFeedService feedService) {
		this.contentService = contentService;
		this.feedService = feedService;
	}

	/** Herkese açık akış: yalnız yayımlanmış içerik (arama, filtre, sayfalama). */
	@GetMapping
	public ContentPageResponse listContents(@Valid ContentFeedRequest request) {
		return feedService.list(request);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ContentResponse createDraft(@Valid @RequestBody CreateContentRequest request) {
		return contentService.createDraft(request);
	}

	/** Yayımlanmış içerik herkese açık; taslak yalnız sahibine görünür. {@code locale}: tr | en (çeviri yoksa ilk dil). */
	@GetMapping("/{contentId}")
	public ContentResponse getContent(@PathVariable UUID contentId,
			@RequestParam(required = false) String locale) {
		return contentService.getContent(contentId, locale);
	}

	@PatchMapping("/{contentId}")
	public ContentResponse updateContent(@PathVariable UUID contentId, @Valid @RequestBody UpdateContentRequest request) {
		return contentService.updateContent(contentId, request);
	}

	@DeleteMapping("/{contentId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteContent(@PathVariable UUID contentId) {
		contentService.deleteContent(contentId);
	}
}
