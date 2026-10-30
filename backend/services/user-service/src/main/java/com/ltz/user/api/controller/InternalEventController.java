package com.ltz.user.api.controller;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.user.api.dto.request.EventEnvelopeRequest;
import com.ltz.user.api.dto.response.EventAckResponse;
import com.ltz.user.application.service.EventIngestService;
import com.ltz.user.application.service.EventOutcome;
import com.ltz.user.application.service.IncomingEvent;
import com.ltz.user.infrastructure.security.TokenClaims;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Servisler arası olay girişi (iç uç, kullanıcıya açık değil). Yalnız {@code scope=events:ingest} taşıyan servis
 * token'ıyla çağrılır. Token'ın {@code sub} değeri zarftaki {@code producer} ile tam olarak {@code svc:<producer>}
 * olmalıdır (bir servis başkası adına olay yayınlayamaz). Auth'un olaylarında token ayrıca olayın hesabına bağlıdır:
 * {@code account_id} claim'i {@code aggregateId} ile aynı olmalıdır. Aksi halde {@code 403} döner ve hiçbir şey
 * işlenmez.
 */
@RestController
@RequiredArgsConstructor
public class InternalEventController {

    private static final String AUTH_PRODUCER = "auth-service";

    private final EventIngestService events;

    @PostMapping(InternalApiPaths.EVENTS)
    public EventAckResponse ingest(@Valid @RequestBody EventEnvelopeRequest request, @AuthenticationPrincipal Jwt token) {
        authorize(request, token);
        EventOutcome outcome = events.ingest(new IncomingEvent(request.eventId(), request.eventType(),
                request.eventVersion(), request.occurredAt(), request.producer(), request.aggregateId(),
                request.payload()));
        return new EventAckResponse(request.eventId(), outcome);
    }

    private static void authorize(EventEnvelopeRequest request, Jwt token) {
        boolean callerMatchesProducer =
                (TokenClaims.SERVICE_SUBJECT_PREFIX + request.producer()).equals(token.getSubject());
        boolean accountBound = !AUTH_PRODUCER.equals(request.producer())
                || request.aggregateId().toString().equals(token.getClaimAsString(TokenClaims.ACCOUNT_ID));
        if (!callerMatchesProducer || !accountBound) {
            throw new AccessDeniedException("Service token is not valid for this event");
        }
    }
}
