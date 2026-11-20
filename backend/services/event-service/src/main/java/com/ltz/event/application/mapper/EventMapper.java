package com.ltz.event.application.mapper;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.ltz.event.api.dto.response.EventPageResponse;
import com.ltz.event.api.dto.response.EventResponse;
import com.ltz.event.api.dto.response.EventSummaryResponse;
import com.ltz.event.api.dto.response.ParticipationResponse;
import com.ltz.event.domain.entity.Event;
import com.ltz.event.domain.entity.EventParticipation;
import com.ltz.event.domain.enums.EventPlatform;
import com.ltz.event.domain.enums.ParticipationStatus;

@Component
public class EventMapper {

	/** Oluşturma yanıtı: izleyici düzenleyicidir; katılım kaydı yoktur ve kendi etkinliğine katılamaz. */
	public EventResponse toResponse(Event event) {
		return toResponse(event, null, false);
	}

	public EventResponse toResponse(Event event, ParticipationStatus viewerParticipation, boolean joinable) {
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
				event.getUpdatedAt(),
				event.getCapacity(),
				event.getParticipantCount(),
				event.getRemainingCapacity(),
				viewerParticipation,
				joinable);
	}

	public EventSummaryResponse toSummary(Event event, ParticipationStatus viewerParticipation, boolean joinable) {
		return new EventSummaryResponse(
				event.getId(),
				event.getOrganizerId(),
				event.getType(),
				event.getGameId(),
				platforms(event),
				event.getTitle(),
				event.getStartsAt(),
				event.getEndsAt(),
				event.getStatus(),
				event.getCapacity(),
				event.getParticipantCount(),
				event.getRemainingCapacity(),
				viewerParticipation,
				joinable);
	}

	public EventPageResponse toPage(Page<Event> page, Function<Event, EventSummaryResponse> summary) {
		return new EventPageResponse(
				page.getContent().stream().map(summary).toList(),
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages(),
				page.hasNext());
	}

	public ParticipationResponse toParticipation(EventParticipation participation) {
		return new ParticipationResponse(
				participation.getId(),
				participation.getEventId(),
				participation.getStatus(),
				participation.getJoinedAt(),
				participation.getCancelledAt());
	}

	/** Platformlar enum sırasıyla (deterministik JSON); belirtilmemişse boş liste. */
	private static List<EventPlatform> platforms(Event event) {
		return List.copyOf(event.getPlatforms());
	}
}
