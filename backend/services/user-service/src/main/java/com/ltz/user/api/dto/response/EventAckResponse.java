package com.ltz.user.api.dto.response;

import java.util.UUID;

import com.ltz.user.application.service.EventOutcome;

/** Olay girişi yanıtı: üretici yalnız 2xx'i "teslim edildi" sayar. */
public record EventAckResponse(UUID eventId, EventOutcome outcome) {
}
