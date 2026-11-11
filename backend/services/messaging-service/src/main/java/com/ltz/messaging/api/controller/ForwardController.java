package com.ltz.messaging.api.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.messaging.api.dto.request.ForwardMessageRequest;
import com.ltz.messaging.api.dto.response.ForwardResponse;
import com.ltz.messaging.application.service.ForwardResult;
import com.ltz.messaging.application.service.MessageForwardService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Mesaj iletme (MSG-20). Kimlik ister. */
@RestController
@RequiredArgsConstructor
public class ForwardController {

    private final MessageForwardService forwards;

    /** Yeni iletmede 201; aynı {@code clientMessageId} ile yeniden denemede 200 ve aynı mesajlar. */
    @PostMapping(MessagingApiPaths.MESSAGE_FORWARD)
    public ResponseEntity<ForwardResponse> forward(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID conversationId,
                                                   @PathVariable UUID messageId,
                                                   @Valid @RequestBody ForwardMessageRequest request) {
        ForwardResult result = forwards.forward(AuthenticatedUser.id(jwt), conversationId, messageId,
                request.clientMessageId(), request.targetConversationIds());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ForwardResponse.from(result));
    }
}
