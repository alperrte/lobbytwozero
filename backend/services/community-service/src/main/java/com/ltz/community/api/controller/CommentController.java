package com.ltz.community.api.controller;

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

import com.ltz.community.api.dto.request.CommentPageRequest;
import com.ltz.community.api.dto.request.CreateCommentRequest;
import com.ltz.community.api.dto.request.UpdateCommentRequest;
import com.ltz.community.api.dto.response.CommentPageResponse;
import com.ltz.community.api.dto.response.CommentResponse;
import com.ltz.community.application.service.CommentFeedService;
import com.ltz.community.application.service.CommentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/community")
public class CommentController {

	private final CommentService commentService;
	private final CommentFeedService commentFeedService;

	public CommentController(CommentService commentService, CommentFeedService commentFeedService) {
		this.commentService = commentService;
		this.commentFeedService = commentFeedService;
	}

	@PostMapping("/posts/{postId}/comments")
	@ResponseStatus(HttpStatus.CREATED)
	public CommentResponse createComment(@PathVariable UUID postId, @Valid @RequestBody CreateCommentRequest request) {
		return commentService.createComment(postId, request);
	}

	@GetMapping("/posts/{postId}/comments")
	public CommentPageResponse listComments(@PathVariable UUID postId, @Valid CommentPageRequest request) {
		return commentFeedService.listComments(postId, request);
	}

	@GetMapping("/comments/{commentId}/replies")
	public CommentPageResponse listReplies(@PathVariable UUID commentId, @Valid CommentPageRequest request) {
		return commentFeedService.listReplies(commentId, request);
	}

	@PatchMapping("/comments/{commentId}")
	public CommentResponse updateComment(@PathVariable UUID commentId,
			@Valid @RequestBody UpdateCommentRequest request) {
		return commentService.updateComment(commentId, request);
	}

	@DeleteMapping("/comments/{commentId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteComment(@PathVariable UUID commentId) {
		commentService.deleteComment(commentId);
	}
}
