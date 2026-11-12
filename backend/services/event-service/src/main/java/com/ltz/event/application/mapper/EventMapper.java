package com.ltz.event.application.mapper;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.ltz.event.api.dto.response.EventPageResponse;
import com.ltz.event.api.dto.response.EventResponse;
import com.ltz.event.api.dto.response.EventSummaryResponse;
import com.ltz.event.domain.entity.Event;
import com.ltz.event.domain.enums.EventPlatform;

@Component
public class EventMapper {

	public EventResponse toResponse(Event event) {
		return new EventResponse(
				event.getId(),
				event.getOrganizerId(),
				event.getType(),
				event.getGameId(),
				platforms(event),
				event.getTitle(),
				event.getDescription(),
				event.getStartsAt(),
				event.getEndsAt(),
				event.getStatus(),
				event.getCreatedAt(),
				event.getUpdatedAt());
	}

	public EventSummaryResponse toSummary(Event event) {
		return new EventSummaryResponse(
				event.getId(),
				event.getOrganizerId(),
				event.getType(),
				event.getGameId(),
				platforms(event),
				event.getTitle(),
				event.getStartsAt(),
				event.getEndsAt(),
				event.getStatus());
	}

	public EventPageResponse toPage(Page<Event> page) {
		return new EventPageResponse(
				page.getContent().stream().map(this::toSummary).toList(),
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages(),
				page.hasNext());
	}

	/** Platformlar enum sırasıyla (deterministik JSON); belirtilmemişse boş liste. */
	private static List<EventPlatform> platforms(Event event) {
		return List.copyOf(event.getPlatforms());
	}
}
