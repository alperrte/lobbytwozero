package com.ltz.community.api.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.community.api.dto.request.CreatePostRequest;
import com.ltz.community.api.dto.request.LinkGameRequest;
import com.ltz.community.api.dto.request.UpdatePostRequest;
import com.ltz.community.api.dto.response.PostResponse;
import com.ltz.community.application.service.PostService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/community/posts")
public class PostController {

	private final PostService postService;

	public PostController(PostService postService) {
		this.postService = postService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public PostResponse createPost(@Valid @RequestBody CreatePostRequest request) {
		return postService.createPost(request);
	}

	@PutMapping("/{postId}/game")
	public PostResponse linkGame(@PathVariable UUID postId, @Valid @RequestBody LinkGameRequest request) {
		return postService.linkGame(postId, request);
	}

	@DeleteMapping("/{postId}/game")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void unlinkGame(@PathVariable UUID postId) {
		postService.unlinkGame(postId);
	}

	@DeleteMapping("/{postId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deletePost(@PathVariable UUID postId) {
		postService.deletePost(postId);
	}

	@PatchMapping("/{postId}")
	public PostResponse updatePost(@PathVariable UUID postId, @Valid @RequestBody UpdatePostRequest request) {
		return postService.updatePost(postId, request);
	}
}
