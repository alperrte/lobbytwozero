package com.ltz.messaging.api.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.messaging.application.service.MessageDeletionService;
import com.ltz.messaging.application.service.MessageHidingService;
import com.ltz.messaging.domain.exception.MessagingErrorCode;
import com.ltz.messaging.domain.exception.MessagingException;

import lombok.RequiredArgsConstructor;

/** Mesaj silme: benden sil (MSG-25) ve herkesten sil (MSG-26). Tüm uçlar kimlik ister. */
@RestController
@RequiredArgsConstructor
public class MessageActionController {

    static final String SCOPE_ME = "me";
    static final String SCOPE_EVERYONE = "everyone";

    private final MessageHidingService hiding;
    private final MessageDeletionService deletion;

    /**
     * {@code scope=me}: yalnız isteyenin görünümünden kaldırır. {@code scope=everyone}: herkesin görünümünde "silindi"
     * olur (gönderen ya da grup yöneticisi, süre içinde). Başka değer {@code 400 INVALID_DELETE_SCOPE}.
     */
    @DeleteMapping(MessagingApiPaths.MESSAGE)
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID conversationId,
                                       @PathVariable UUID messageId,
                                       @RequestParam(required = false) String scope) {
        UUID actor = AuthenticatedUser.id(jwt);
        if (SCOPE_ME.equals(scope)) {
            hiding.hide(actor, conversationId, messageId);
        } else if (SCOPE_EVERYONE.equals(scope)) {
            deletion.deleteForEveryone(actor, conversationId, messageId);
        } else {
            throw new MessagingException(MessagingErrorCode.INVALID_DELETE_SCOPE);
        }
        return ResponseEntity.noContent().build();
    }
}
