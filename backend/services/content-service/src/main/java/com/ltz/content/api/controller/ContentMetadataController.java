package com.ltz.content.api.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.content.api.dto.request.LinkGameRequest;
import com.ltz.content.api.dto.request.ReplaceCategoriesRequest;
import com.ltz.content.api.dto.request.ReplaceSourcesRequest;
import com.ltz.content.api.dto.response.ContentResponse;
import com.ltz.content.application.service.ContentMetadataService;

import jakarta.validation.Valid;

/** İçerik üst verisi: kategoriler (CONT-03), kaynaklar (CONT-16) ve ilişkili oyun (CONT-01/13). */
@RestController
@RequestMapping("/api/v1/content/contents/{contentId}")
public class ContentMetadataController {

	private final ContentMetadataService metadataService;

	public ContentMetadataController(ContentMetadataService metadataService) {
		this.metadataService = metadataService;
	}

	@PutMapping("/categories")
	public ContentResponse replaceCategories(@PathVariable UUID contentId,
			@Valid @RequestBody ReplaceCategoriesRequest request) {
		return metadataService.replaceCategories(contentId, request);
	}

	@PutMapping("/sources")
	public ContentResponse replaceSources(@PathVariable UUID contentId, @Valid @RequestBody ReplaceSourcesRequest request) {
		return metadataService.replaceSources(contentId, request);
	}

	@PutMapping("/game")
	public ContentResponse linkGame(@PathVariable UUID contentId, @Valid @RequestBody LinkGameRequest request) {
		return metadataService.linkGame(contentId, request.gameId());
	}

	@DeleteMapping("/game")
	public ContentResponse unlinkGame(@PathVariable UUID contentId) {
		return metadataService.unlinkGame(contentId);
	}
}
