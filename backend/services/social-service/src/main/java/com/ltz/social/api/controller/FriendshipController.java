package com.ltz.social.api.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.social.api.dto.response.FriendPageResponse;
import com.ltz.social.application.service.FriendshipService;

import lombok.RequiredArgsConstructor;

/** Arkadaş listesi ve arkadaşlıktan çıkarma (SOC-07, SOC-08). Kullanıcı yalnız kendi arkadaşlıklarını görür. */
@RestController
@RequiredArgsConstructor
public class FriendshipController {

    private final FriendshipService friendshipService;

    @GetMapping(SocialApiPaths.FRIENDS)
    public FriendPageResponse list(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) Integer limit,
                                   @RequestParam(required = false) String cursor) {
        return FriendPageResponse.from(friendshipService.listFriends(AuthenticatedUser.id(jwt), limit, cursor));
    }

    /** Başka bir kullanıcının arkadaş listesi (SOC-12): engel ve görünürlük tercihi sunucuda uygulanır. */
    @GetMapping(SocialApiPaths.USER_FRIENDS)
    public FriendPageResponse listOf(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId,
                                     @RequestParam(required = false) Integer limit,
                                     @RequestParam(required = false) String cursor) {
        return FriendPageResponse.from(
                friendshipService.listFriendsOf(AuthenticatedUser.id(jwt), userId, limit, cursor));
    }

    @DeleteMapping(SocialApiPaths.FRIEND)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfriend(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID friendId) {
        friendshipService.unfriend(AuthenticatedUser.id(jwt), friendId);
    }
}
