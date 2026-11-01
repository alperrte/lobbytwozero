package com.ltz.messaging.api.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.messaging.api.dto.request.AddMembersRequest;
import com.ltz.messaging.api.dto.request.ChangeMemberRoleRequest;
import com.ltz.messaging.api.dto.response.ConversationResponse;
import com.ltz.messaging.application.service.GroupMembershipService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Grup üyeliği (MSG-03), ayrılma (MSG-04) ve üye çıkarma (MSG-05). Üyelik ve rol kontrolü servis katmanındadır;
 * yapan kullanıcı yalnız token'dan alınır.
 */
@RestController
@RequiredArgsConstructor
public class GroupMemberController {

    private final GroupMembershipService memberships;

    @PostMapping(MessagingApiPaths.MEMBERS)
    public ConversationResponse addMembers(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID conversationId,
                                           @Valid @RequestBody AddMembersRequest request) {
        return ConversationResponse.from(
                memberships.addMembers(AuthenticatedUser.id(jwt), conversationId, request.userIds()));
    }

    @DeleteMapping(MessagingApiPaths.MEMBER)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID conversationId,
                             @PathVariable UUID userId) {
        memberships.removeMember(AuthenticatedUser.id(jwt), conversationId, userId);
    }

    @PutMapping(MessagingApiPaths.MEMBER_ROLE)
    public ConversationResponse changeRole(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID conversationId,
                                           @PathVariable UUID userId,
                                           @Valid @RequestBody ChangeMemberRoleRequest request) {
        return ConversationResponse.from(
                memberships.changeRole(AuthenticatedUser.id(jwt), conversationId, userId, request.role()));
    }

    @PostMapping(MessagingApiPaths.LEAVE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID conversationId) {
        memberships.leave(AuthenticatedUser.id(jwt), conversationId);
    }
}
