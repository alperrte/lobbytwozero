package com.ltz.content.api.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.content.api.dto.response.ModerationActionResponse;
import com.ltz.content.application.service.ContentModerationService;

/**
 * Moderasyon geçmişi (CONT-10), salt okunur; yalnız MODERATOR / ADMIN. Yayından kaldırma ve geri alma bu serviste doğrudan
 * yapılamaz: karar admin-service'in {@code ModerationActionTaken} olayıyla gelir ({@code POST /internal/events}).
 */
@RestController
@RequestMapping("/api/v1/content/moderation/contents/{contentId}/actions")
public class ContentModerationController {

	private final ContentModerationService moderationService;

	public ContentModerationController(ContentModerationService moderationService) {
		this.moderationService = moderationService;
	}

	@GetMapping
	public List<ModerationActionResponse> history(@PathVariable UUID contentId) {
		return moderationService.history(contentId);
	}
}
