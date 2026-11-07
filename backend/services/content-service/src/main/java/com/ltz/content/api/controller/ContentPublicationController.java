package com.ltz.content.api.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.content.api.dto.response.ContentResponse;
import com.ltz.content.application.service.ContentPublishingService;

/** Yayınlama (CONT-09) ve öne çıkarma (CONT-11) uçları. */
@RestController
@RequestMapping("/api/v1/content/contents/{contentId}")
public class ContentPublicationController {

	private final ContentPublishingService publishingService;

	public ContentPublicationController(ContentPublishingService publishingService) {
		this.publishingService = publishingService;
	}

	@PostMapping("/publish")
	public ContentResponse publish(@PathVariable UUID contentId) {
		return publishingService.publish(contentId);
	}

	@PutMapping("/featured")
	public ContentResponse feature(@PathVariable UUID contentId) {
		return publishingService.feature(contentId);
	}

	@DeleteMapping("/featured")
	public ContentResponse unfeature(@PathVariable UUID contentId) {
		return publishingService.unfeature(contentId);
	}
}
