package com.ltz.messaging.api.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.messaging.api.dto.request.MarkReadRequest;
import com.ltz.messaging.api.dto.request.SendMessageRequest;
import com.ltz.messaging.api.dto.response.MessagePageResponse;
import com.ltz.messaging.api.dto.response.MessageResponse;
import com.ltz.messaging.api.dto.response.ReadStateResponse;
import com.ltz.messaging.application.service.MessageService;
import com.ltz.messaging.application.service.SendMessageResult;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Metin mesajı gönderme (MSG-06), mesaj geçmişi (MSG-07/10) ve okuma işaretçisi (MSG-13). Tüm uçlar kimlik ister. */
@RestController
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messages;

    /** Yeni mesajda 201; aynı {@code clientMessageId} ile yeniden denemede 200 ve aynı mesaj. */
    @PostMapping(MessagingApiPaths.MESSAGES)
    public ResponseEntity<MessageResponse> send(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID conversationId,
                                                @Valid @RequestBody SendMessageRequest request) {
        SendMessageResult result = messages.send(AuthenticatedUser.id(jwt), conversationId, request.clientMessageId(),
                request.body());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(MessageResponse.from(result.message()));
    }

    @GetMapping(MessagingApiPaths.MESSAGES)
    public MessagePageResponse history(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID conversationId,
                                       @RequestParam(required = false) Long before,
                                       @RequestParam(required = false) Long after,
                                       @RequestParam(required = false) Integer limit) {
        return MessagePageResponse.from(
                messages.history(AuthenticatedUser.id(jwt), conversationId, before, after, limit));
    }

    @PostMapping(MessagingApiPaths.READ)
    public ReadStateResponse markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID conversationId,
                                      @Valid @RequestBody MarkReadRequest request) {
        return ReadStateResponse.from(messages.markRead(AuthenticatedUser.id(jwt), conversationId, request.seq()));
    }
}
