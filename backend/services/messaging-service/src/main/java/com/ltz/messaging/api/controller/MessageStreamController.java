package com.ltz.messaging.api.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ltz.messaging.application.service.MessageStreamService;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * MSG-09 gerçek zamanlı akış (SSE). Tarayıcı {@code EventSource} ile Gateway üzerinden bağlanır (çerez → Bearer
 * Gateway'de). Akış token'ın bitişinde kapanır; {@code EventSource} kendiliğinden yeniden bağlanır.
 */
@RestController
@RequiredArgsConstructor
public class MessageStreamController {

    private final MessageStreamService streams;

    @GetMapping(value = MessagingApiPaths.STREAM, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal Jwt jwt, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        // Ara proxy'lerin (nginx vb.) akışı tamponlamaması için.
        response.setHeader("X-Accel-Buffering", "no");
        return streams.open(AuthenticatedUser.id(jwt), jwt.getExpiresAt());
    }
}
