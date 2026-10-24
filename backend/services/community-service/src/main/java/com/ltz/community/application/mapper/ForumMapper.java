package com.ltz.community.application.mapper;

import org.springframework.stereotype.Component;

import com.ltz.community.api.dto.response.ForumResponse;
import com.ltz.community.domain.entity.Forum;

@Component
public class ForumMapper {

	public ForumResponse toResponse(Forum forum) {
		return new ForumResponse(
				forum.getId(),
				forum.getGameId(),
				forum.getSlug(),
				forum.getTitle(),
				forum.getDescription(),
				forum.getStatus(),
				forum.getCreatedAt(),
				forum.getUpdatedAt());
	}
}
