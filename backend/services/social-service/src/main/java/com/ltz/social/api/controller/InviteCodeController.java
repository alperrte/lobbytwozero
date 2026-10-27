package com.ltz.social.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.social.api.dto.request.RedeemInviteCodeRequest;
import com.ltz.social.api.dto.response.FriendRequestResponse;
import com.ltz.social.api.dto.response.InviteCodeResponse;
import com.ltz.social.application.service.InviteCodeResult;
import com.ltz.social.application.service.InviteCodeService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Arkadaşlık davet kodu (SOC-14): kendi kodunu yönet, başkasının kodunu girerek arkadaşlık isteği başlat. */
@RestController
@RequiredArgsConstructor
public class InviteCodeController {

    private final InviteCodeService inviteCodes;

    @GetMapping(SocialApiPaths.INVITE_CODE)
    public InviteCodeResponse current(@AuthenticationPrincipal Jwt jwt) {
        return InviteCodeResponse.from(inviteCodes.find(AuthenticatedUser.id(jwt)));
    }

    /** Etkin kod varsa 200 ile onu, yoksa yeni üretip 201 ile döner (idempotent). */
    @PostMapping(SocialApiPaths.INVITE_CODE)
    public ResponseEntity<InviteCodeResponse> getOrCreate(@AuthenticationPrincipal Jwt jwt) {
        InviteCodeResult result = inviteCodes.getOrCreate(AuthenticatedUser.id(jwt));
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(InviteCodeResponse.from(result.code()));
    }

    @DeleteMapping(SocialApiPaths.INVITE_CODE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal Jwt jwt) {
        inviteCodes.revoke(AuthenticatedUser.id(jwt));
    }

    @PostMapping(SocialApiPaths.FRIEND_REQUESTS_BY_CODE)
    @ResponseStatus(HttpStatus.CREATED)
    public FriendRequestResponse redeem(@AuthenticationPrincipal Jwt jwt,
                                        @Valid @RequestBody RedeemInviteCodeRequest request) {
        return FriendRequestResponse.from(inviteCodes.redeem(AuthenticatedUser.id(jwt), request.code()));
    }
}
