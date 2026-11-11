package com.ltz.messaging.api.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.messaging.api.dto.request.ReactionRequest;
import com.ltz.messaging.api.dto.response.MessageReactionsResponse;
import com.ltz.messaging.api.dto.response.ReactionsResponse;
import com.ltz.messaging.application.service.ReactionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Mesaj tepkileri (MSG-23): ver/değiştir, kaldır, listele. Tüm uçlar kimlik ister. */
@RestController
@RequiredArgsConstructor
public class ReactionController {

    private final ReactionService reactions;

    /** Tepki koyar ya da isteyenin önceki tepkisinin yerine yazar; güncel özeti döner. */
    @PutMapping(MessagingApiPaths.MESSAGE_REACTION)
    public ReactionsResponse react(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID conversationId,
                                   @PathVariable UUID messageId, @Valid @RequestBody ReactionRequest request) {
        return ReactionsResponse.from(
                reactions.react(AuthenticatedUser.id(jwt), conversationId, messageId, request.emoji()));
    }

    /** İsteyenin tepkisini kaldırır; tepki yoksa da {@code 204}. */
    @DeleteMapping(MessagingApiPaths.MESSAGE_REACTION)
    public ResponseEntity<Void> unreact(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID conversationId,
                                        @PathVariable UUID messageId) {
        reactions.unreact(AuthenticatedUser.id(jwt), conversationId, messageId);
        return ResponseEntity.noContent().build();
    }

    /** Mesajı görebilen her aktif üye görür. */
    @GetMapping(MessagingApiPaths.MESSAGE_REACTIONS)
    public MessageReactionsResponse list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID conversationId,
                                         @PathVariable UUID messageId) {
        return MessageReactionsResponse.from(reactions.list(AuthenticatedUser.id(jwt), conversationId, messageId));
    }
}
