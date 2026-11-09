package com.ltz.event.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.event.api.dto.request.CreateEventRequest;
import com.ltz.event.api.dto.response.EventResponse;
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
}
