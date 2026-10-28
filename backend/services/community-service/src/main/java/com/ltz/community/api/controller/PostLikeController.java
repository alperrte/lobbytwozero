package com.ltz.community.api.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.community.api.dto.response.LikeStateResponse;
import com.ltz.community.application.service.PostLikeService;

@RestController
@RequestMapping("/api/v1/community/posts/{postId}/like")
public class PostLikeController {

	private final PostLikeService postLikeService;

	public PostLikeController(PostLikeService postLikeService) {
		this.postLikeService = postLikeService;
	}

	@PutMapping
	public LikeStateResponse like(@PathVariable UUID postId) {
		return postLikeService.like(postId);
	}

	@DeleteMapping
	public LikeStateResponse unlike(@PathVariable UUID postId) {
		return postLikeService.unlike(postId);
	}
}
