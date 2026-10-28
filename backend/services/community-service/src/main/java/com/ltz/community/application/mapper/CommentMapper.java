package com.ltz.community.application.mapper;

import org.springframework.stereotype.Component;

import com.ltz.community.api.dto.response.CommentResponse;
import com.ltz.community.domain.entity.Comment;
import com.ltz.community.domain.enums.CommentStatus;

@Component
public class CommentMapper {

	/** Silinmiş yorumun içeriği ve yazarı yanıta yazılmaz; yalnız yanıt ilişkisini taşıyan iskelet döner. */
	public CommentResponse toResponse(Comment comment, int replyCount) {
		boolean deleted = comment.getStatus() == CommentStatus.DELETED;
		return new CommentResponse(
				comment.getId(),
				comment.getPostId(),
				deleted ? null : comment.getAuthorId(),
				comment.getParentCommentId(),
				deleted ? null : comment.getBody(),
				comment.getStatus(),
				comment.getCreatedAt(),
				comment.getUpdatedAt(),
				replyCount);
	}
}
