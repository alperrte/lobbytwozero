package com.ltz.community.application.mapper;

import org.springframework.stereotype.Component;

import com.ltz.community.api.dto.response.PostImageResponse;
import com.ltz.community.api.dto.response.PostResponse;
import com.ltz.community.domain.entity.Post;
import com.ltz.community.domain.entity.PostImage;

@Component
public class PostMapper {

	public PostResponse toResponse(Post post) {
		return new PostResponse(
				post.getId(),
				post.getAuthorId(),
				post.getGameId(),
				post.getBody(),
				post.getStatus(),
				post.getCreatedAt(),
				post.getUpdatedAt());
	}

	public PostImageResponse toResponse(PostImage image) {
		return new PostImageResponse(
				image.getId(),
				image.getPostId(),
				image.getMimeType(),
				image.getSizeBytes(),
				image.getWidth(),
				image.getHeight(),
				image.getSortOrder(),
				"/api/v1/community/posts/" + image.getPostId() + "/images/" + image.getId(),
				image.getCreatedAt());
	}
}
