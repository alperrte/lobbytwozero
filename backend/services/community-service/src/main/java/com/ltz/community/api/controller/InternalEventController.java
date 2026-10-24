package com.ltz.community.api.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.community.api.dto.request.InboundEventRequest;
import com.ltz.community.api.dto.response.InboundEventResult;
import com.ltz.community.application.service.InboundEventService;

import jakarta.validation.Valid;

/**
 * Servisler arası olay girişi. Gateway'de route edilmez; yalnız {@code SCOPE_internal.events} yetkisi taşıyan servis
 * kimliğini kabul eder (bkz. {@code SecurityConfig}). Servisler arası kimlik modeli ADR'si netleşene kadar bu yetki
 * hiçbir token'da bulunmadığından uç fiilen kapalıdır (fail-closed).
 */
@RestController
@RequestMapping("/internal/events")
public class InternalEventController {

	private final InboundEventService inboundEventService;

	public InternalEventController(InboundEventService inboundEventService) {
		this.inboundEventService = inboundEventService;
	}

	@PostMapping
	public InboundEventResult receive(@Valid @RequestBody InboundEventRequest event) {
		return inboundEventService.handle(event);
	}
}
