package com.ltz.event.application.mapper;

import org.springframework.stereotype.Component;

import com.ltz.event.api.dto.response.EventResponse;
import com.ltz.event.domain.entity.Event;

@Component
public class EventMapper {

	public EventResponse toResponse(Event event) {
		return new EventResponse(
				event.getId(),
				event.getOrganizerId(),
				event.getType(),
				event.getTitle(),
				event.getDescription(),
				event.getStartsAt(),
				event.getEndsAt(),
				event.getStatus(),
				event.getCreatedAt(),
				event.getUpdatedAt());
	}
}
