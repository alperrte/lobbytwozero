package com.ltz.community.api.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.community.api.dto.request.CreateForumRequest;
import com.ltz.community.api.dto.request.ForumListRequest;
import com.ltz.community.api.dto.request.UpdateForumRequest;
import com.ltz.community.api.dto.response.ForumPageResponse;
import com.ltz.community.api.dto.response.ForumResponse;
import com.ltz.community.application.service.ForumService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/community/forums")
public class ForumController {

	private final ForumService forumService;

	public ForumController(ForumService forumService) {
		this.forumService = forumService;
	}

	@GetMapping
	public ForumPageResponse listForums(@Valid ForumListRequest request) {
		return forumService.listForums(request);
	}

	@GetMapping("/{forumId}")
	public ForumResponse getForum(@PathVariable UUID forumId) {
		return forumService.getForum(forumId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ForumResponse createForum(@Valid @RequestBody CreateForumRequest request) {
		return forumService.createForum(request);
	}

	@PatchMapping("/{forumId}")
	public ForumResponse updateForum(@PathVariable UUID forumId, @Valid @RequestBody UpdateForumRequest request) {
		return forumService.updateForum(forumId, request);
	}
}
