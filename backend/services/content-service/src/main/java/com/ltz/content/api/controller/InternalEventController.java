package com.ltz.content.api.controller;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.content.api.dto.request.InboundEventRequest;
import com.ltz.content.api.dto.response.InboundEventResult;
import com.ltz.content.application.service.InboundEventService;
import com.ltz.content.infrastructure.security.TokenClaims;

import jakarta.validation.Valid;

/**
 * Servisler arası olay girişi. Gateway'de route edilmez; yalnız Auth'un imzaladığı servis token'ını ({@code svc+jwt},
 * {@code aud=content-service}) ve {@code SCOPE_events:ingest} yetkisini kabul eder (bkz. {@code SecurityConfig}). Zarftaki
 * {@code producer} alanı kendi başına güvenilmez: token'ın çağıranı ({@code sub}) tam olarak {@code svc:<producer>}
 * olmalıdır, böylece bir servis başka bir servis adına olay yayınlayamaz.
 */
@RestController
@RequestMapping(InternalApiPaths.EVENTS)
public class InternalEventController {

	private final InboundEventService inboundEventService;

	public InternalEventController(InboundEventService inboundEventService) {
		this.inboundEventService = inboundEventService;
	}

	@PostMapping
	public InboundEventResult receive(@Valid @RequestBody InboundEventRequest event, @AuthenticationPrincipal Jwt token) {
		if (!(TokenClaims.SERVICE_SUBJECT_PREFIX + event.producer()).equals(token.getSubject())) {
			throw new AccessDeniedException("The service token does not belong to the declared producer");
		}
		return inboundEventService.handle(event);
	}
}
