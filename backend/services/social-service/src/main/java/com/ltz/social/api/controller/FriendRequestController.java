package com.ltz.social.api.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.social.api.dto.request.SendFriendRequestRequest;
import com.ltz.social.api.dto.response.FriendRequestPageResponse;
import com.ltz.social.api.dto.response.FriendRequestResponse;
import com.ltz.social.application.service.FriendRequestService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Arkadaşlık istekleri (SOC-01..06). Tüm uçlar kimlik ister; sahiplik kontrolü servis katmanındadır. */
@RestController
@RequiredArgsConstructor
public class FriendRequestController {

    private final FriendRequestService friendRequests;

    @PostMapping(SocialApiPaths.FRIEND_REQUESTS)
    @ResponseStatus(HttpStatus.CREATED)
    public FriendRequestResponse send(@AuthenticationPrincipal Jwt jwt,
                                      @Valid @RequestBody SendFriendRequestRequest request) {
        return FriendRequestResponse.from(friendRequests.send(AuthenticatedUser.id(jwt), request.receiverId()));
    }

    @PostMapping(SocialApiPaths.FRIEND_REQUEST_ACCEPT)
    public FriendRequestResponse accept(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID requestId) {
        return FriendRequestResponse.from(friendRequests.accept(AuthenticatedUser.id(jwt), requestId));
    }

    @PostMapping(SocialApiPaths.FRIEND_REQUEST_REJECT)
    public FriendRequestResponse reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID requestId) {
        return FriendRequestResponse.from(friendRequests.reject(AuthenticatedUser.id(jwt), requestId));
    }

    @PostMapping(SocialApiPaths.FRIEND_REQUEST_CANCEL)
    public FriendRequestResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID requestId) {
        return FriendRequestResponse.from(friendRequests.cancel(AuthenticatedUser.id(jwt), requestId));
    }

    @GetMapping(SocialApiPaths.FRIEND_REQUESTS_INCOMING)
    public FriendRequestPageResponse incoming(@AuthenticationPrincipal Jwt jwt,
                                              @RequestParam(required = false) Integer limit,
                                              @RequestParam(required = false) String cursor) {
        return FriendRequestPageResponse.from(friendRequests.listIncoming(AuthenticatedUser.id(jwt), limit, cursor));
    }

    @GetMapping(SocialApiPaths.FRIEND_REQUESTS_OUTGOING)
    public FriendRequestPageResponse outgoing(@AuthenticationPrincipal Jwt jwt,
                                              @RequestParam(required = false) Integer limit,
                                              @RequestParam(required = false) String cursor) {
        return FriendRequestPageResponse.from(friendRequests.listOutgoing(AuthenticatedUser.id(jwt), limit, cursor));
    }
}
