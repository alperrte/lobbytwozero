package com.ltz.community.api.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.community.api.dto.request.GameContentListRequest;
import com.ltz.community.api.dto.response.GameContentDetailResponse;
import com.ltz.community.api.dto.response.GameContentPageResponse;
import com.ltz.community.application.service.GameContentService;

import jakarta.validation.Valid;

/** Oyuna bağlı rehber, easter egg ve oyuncu deneyimi içeriğinin herkese açık okuması (GAME-11). */
@RestController
@RequestMapping("/api/v1/community/games/{gameId}/content")
public class GameContentController {

	private final GameContentService gameContentService;

	public GameContentController(GameContentService gameContentService) {
		this.gameContentService = gameContentService;
	}

	@GetMapping
	public GameContentPageResponse list(@PathVariable UUID gameId, @Valid GameContentListRequest request) {
		return gameContentService.list(gameId, request);
	}

	@GetMapping("/{contentId}")
	public GameContentDetailResponse detail(@PathVariable UUID gameId, @PathVariable UUID contentId) {
		return gameContentService.detail(gameId, contentId);
	}
}
