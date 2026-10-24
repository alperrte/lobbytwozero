package com.ltz.community.application.mapper;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ltz.community.api.dto.response.PostImageResponse;
import com.ltz.community.api.dto.response.PostResponse;
import com.ltz.community.api.dto.response.PostViewResponse;
import com.ltz.community.api.dto.response.RepostSourceResponse;
import com.ltz.community.domain.entity.Post;
import com.ltz.community.domain.entity.PostImage;
import com.ltz.community.infrastructure.persistence.repository.PostImageMeta;

@Component
public class PostMapper {

	public PostResponse toResponse(Post post) {
		return new PostResponse(
				post.getId(),
				post.getAuthorId(),
				post.getGameId(),
				post.getForumId(),
				post.getCategory(),
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
				imageUrl(image.getPostId(), image.getId()),
				image.getCreatedAt());
	}

	public PostViewResponse toView(Post post, List<PostImageMeta> images, boolean likedByMe,
			RepostSourceResponse repostOf) {
		return new PostViewResponse(
				post.getId(),
				post.getAuthorId(),
				post.getGameId(),
				post.getForumId(),
				post.getCategory(),
				post.getBody(),
				post.getStatus(),
				post.getCreatedAt(),
				post.getUpdatedAt(),
				post.getLikeCount(),
				post.getCommentCount(),
				likedByMe,
				images.stream().map(this::toResponse).toList(),
				repostOf);
	}

	/** Orijinal gönderi erişilebilirse sahibi ve içeriğiyle, değilse içeriksiz iskelet olarak. */
	public RepostSourceResponse toSource(Post original, List<PostImageMeta> images) {
		return new RepostSourceResponse(
				original.getId(),
				true,
				original.getAuthorId(),
				original.getGameId(),
				original.getCategory(),
				original.getBody(),
				original.getCreatedAt(),
				images.stream().map(this::toResponse).toList());
	}

	public PostImageResponse toResponse(PostImageMeta image) {
		return new PostImageResponse(
				image.getId(),
				image.getPostId(),
				image.getMimeType(),
				image.getSizeBytes(),
				image.getWidth(),
				image.getHeight(),
				image.getSortOrder(),
				imageUrl(image.getPostId(), image.getId()),
				image.getCreatedAt());
	}

	private String imageUrl(UUID postId, UUID imageId) {
		return "/api/v1/community/posts/" + postId + "/images/" + imageId;
	}
}
