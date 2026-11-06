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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.content.api.dto.request.CreateContentRequest;
import com.ltz.content.api.dto.request.UpdateContentRequest;
import com.ltz.content.api.dto.response.ContentResponse;
import com.ltz.content.application.service.ContentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/content/contents")
public class ContentController {

	private final ContentService contentService;

	public ContentController(ContentService contentService) {
		this.contentService = contentService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ContentResponse createDraft(@Valid @RequestBody CreateContentRequest request) {
		return contentService.createDraft(request);
	}

	@GetMapping("/{contentId}")
	public ContentResponse getContent(@PathVariable UUID contentId) {
		return contentService.getContent(contentId);
	}

	@PatchMapping("/{contentId}")
	public ContentResponse updateDraft(@PathVariable UUID contentId, @Valid @RequestBody UpdateContentRequest request) {
		return contentService.updateDraft(contentId, request);
	}

	@DeleteMapping("/{contentId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteContent(@PathVariable UUID contentId) {
		contentService.deleteContent(contentId);
	}
}
