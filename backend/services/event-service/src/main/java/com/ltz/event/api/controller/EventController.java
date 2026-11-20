package com.ltz.event.api.controller;

import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.event.api.dto.request.CreateEventRequest;
import com.ltz.event.api.dto.request.EventListRequest;
import com.ltz.event.api.dto.response.EventPageResponse;
import com.ltz.event.api.dto.response.EventResponse;
import com.ltz.event.api.dto.response.ParticipationResponse;
import com.ltz.event.application.service.EventService;

import jakarta.validation.Valid;

@RestController
@RequestMapping(EventController.BASE_PATH)
public class EventController {

	/** Gateway yolu StripPrefix olmadan iletir; servis tam yolu karşılar (docs/architecture.md §4). */
	public static final String BASE_PATH = "/api/v1/event/events";

	private final EventService eventService;

	public EventController(EventService eventService) {
		this.eventService = eventService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public EventResponse createEvent(@Valid @RequestBody CreateEventRequest request) {
		return eventService.createEvent(request);
	}

	/** Yanıt izleyiciye özel alanlar (viewerParticipation, joinable) taşır; paylaşımlı önbelleğe alınmamalıdır. */
	@GetMapping
	public ResponseEntity<EventPageResponse> listEvents(@Valid EventListRequest request) {
		return ResponseEntity.ok().cacheControl(CacheControl.empty().cachePrivate()).body(eventService.listEvents(request));
	}

	@GetMapping("/{eventId}")
	public ResponseEntity<EventResponse> getEvent(@PathVariable UUID eventId) {
		return ResponseEntity.ok().cacheControl(CacheControl.empty().cachePrivate()).body(eventService.getEvent(eventId));
	}

	@PostMapping("/{eventId}/participations")
	@ResponseStatus(HttpStatus.CREATED)
	public ParticipationResponse joinEvent(@PathVariable UUID eventId) {
		return eventService.joinEvent(eventId);
	}

	@DeleteMapping("/{eventId}/participations/me")
	public ParticipationResponse cancelParticipation(@PathVariable UUID eventId) {
		return eventService.cancelParticipation(eventId);
	}
}
