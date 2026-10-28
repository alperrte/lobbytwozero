package com.ltz.community.api.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.community.api.dto.request.CreateRepostRequest;
import com.ltz.community.api.dto.response.PostViewResponse;
import com.ltz.community.application.service.RepostService;

import jakarta.validation.Valid;

/** Repost'u kaldırmak için repost'un kendisi {@code DELETE /posts/{repostId}} ile silinir. */
@RestController
@RequestMapping("/api/v1/community/posts/{postId}/reposts")
public class RepostController {

	private final RepostService repostService;

	public RepostController(RepostService repostService) {
		this.repostService = repostService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public PostViewResponse repost(@PathVariable UUID postId,
			@Valid @RequestBody(required = false) CreateRepostRequest request) {
		return repostService.repost(postId, request);
	}
}
