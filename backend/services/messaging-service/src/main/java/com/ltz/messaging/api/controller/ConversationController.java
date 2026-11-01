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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.messaging.api.dto.request.CreateGroupConversationRequest;
import com.ltz.messaging.api.dto.request.OpenDirectConversationRequest;
import com.ltz.messaging.api.dto.response.ConversationPageResponse;
import com.ltz.messaging.api.dto.response.ConversationResponse;
import com.ltz.messaging.application.service.ConversationListService;
import com.ltz.messaging.application.service.ConversationService;
import com.ltz.messaging.application.service.DirectConversationResult;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Birebir sohbet (MSG-01), grup oluşturma (MSG-02), sohbet listesi (MSG-08, MSG-13) ve sohbet görüntüleme (MSG-14).
 * Tüm uçlar kimlik ister.
 */
@RestController
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversations;
    private final ConversationListService conversationList;

    /** Kullanıcının aktif sohbetleri, son etkinlikten eskiye; son mesaj ve okunmamış sayısıyla. */
    @GetMapping(MessagingApiPaths.CONVERSATIONS)
    public ConversationPageResponse list(@AuthenticationPrincipal Jwt jwt,
                                         @RequestParam(required = false) Integer limit,
                                         @RequestParam(required = false) String cursor) {
        return ConversationPageResponse.from(conversationList.list(AuthenticatedUser.id(jwt), limit, cursor));
    }

    /** Yeni sohbette 201, iki kullanıcı arasında zaten varsa 200 ve aynı sohbet. */
    @PostMapping(MessagingApiPaths.DIRECT_CONVERSATIONS)
    public ResponseEntity<ConversationResponse> openDirect(@AuthenticationPrincipal Jwt jwt,
                                                           @Valid @RequestBody OpenDirectConversationRequest request) {
        DirectConversationResult result = conversations.openDirect(AuthenticatedUser.id(jwt), request.userId());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ConversationResponse.from(result.conversation()));
    }

    @PostMapping(MessagingApiPaths.GROUP_CONVERSATIONS)
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationResponse createGroup(@AuthenticationPrincipal Jwt jwt,
                                            @Valid @RequestBody CreateGroupConversationRequest request) {
        return ConversationResponse.from(
                conversations.createGroup(AuthenticatedUser.id(jwt), request.title(), request.memberIds()));
    }

    @GetMapping(MessagingApiPaths.CONVERSATION)
    public ConversationResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID conversationId) {
        return ConversationResponse.from(conversations.get(AuthenticatedUser.id(jwt), conversationId));
    }
}
