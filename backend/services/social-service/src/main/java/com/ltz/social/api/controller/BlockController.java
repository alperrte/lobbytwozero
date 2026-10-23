package com.ltz.social.api.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.social.api.dto.request.BlockUserRequest;
import com.ltz.social.api.dto.response.BlockPageResponse;
import com.ltz.social.api.dto.response.BlockResponse;
import com.ltz.social.application.service.BlockResult;
import com.ltz.social.application.service.BlockService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Kullanıcı engelleme (SOC-09, SOC-10, SOC-11). Kullanıcı yalnız kendi engellerini yönetir ve görür. */
@RestController
@RequiredArgsConstructor
public class BlockController {

    private final BlockService blockService;

    /** Yeni engelde 201, zaten engelliyse 200 (idempotent). */
    @PostMapping(SocialApiPaths.BLOCKS)
    public ResponseEntity<BlockResponse> block(@AuthenticationPrincipal Jwt jwt,
                                               @Valid @RequestBody BlockUserRequest request) {
        BlockResult result = blockService.block(AuthenticatedUser.id(jwt), request.blockedUserId());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(BlockResponse.from(result.block()));
    }

    @DeleteMapping(SocialApiPaths.BLOCK)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID blockedUserId) {
        blockService.unblock(AuthenticatedUser.id(jwt), blockedUserId);
    }

    @GetMapping(SocialApiPaths.BLOCKS)
    public BlockPageResponse list(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) Integer limit,
                                  @RequestParam(required = false) String cursor) {
        return BlockPageResponse.from(blockService.listBlocked(AuthenticatedUser.id(jwt), limit, cursor));
    }
}
