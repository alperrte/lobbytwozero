package com.ltz.community.api.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.community.api.dto.request.PostFeedRequest;
import com.ltz.community.api.dto.response.PostPageResponse;
import com.ltz.community.application.service.PostFeedService;

import jakarta.validation.Valid;

/** Kullanıcının gönderi geçmişi (profil): aynı arama/filtre/sıralama/sayfalama parametreleriyle. */
@RestController
@RequestMapping("/api/v1/community/users/{userId}/posts")
public class UserPostController {

	private final PostFeedService postFeedService;

	public UserPostController(PostFeedService postFeedService) {
		this.postFeedService = postFeedService;
	}

	@GetMapping
	public PostPageResponse listUserPosts(@PathVariable UUID userId, @Valid PostFeedRequest request) {
		return postFeedService.listUserPosts(userId, request);
	}
}
