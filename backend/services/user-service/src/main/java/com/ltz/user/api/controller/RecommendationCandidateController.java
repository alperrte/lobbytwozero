package com.ltz.user.api.controller;

import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ltz.user.api.dto.response.RecommendationCandidatesResponse;
import com.ltz.user.application.service.RecommendationCandidateService;
import com.ltz.user.infrastructure.security.TokenClaims;

import lombok.RequiredArgsConstructor;

/**
 * Recommendation → User aday keşfi (iç uç, kullanıcıya açık değil; REC-13). Yalnız {@code scope=candidates:read}
 * taşıyan servis token'ıyla ({@code svc+jwt}) çağrılır; çağıran tam olarak {@code svc:recommendation-service} olmalı ve
 * token'ın {@code account_id} claim'i yoldaki {@code accountId} (isteği yapan oyuncu) ile aynı olmalıdır, aksi halde
 * {@code 403}. İzleyici kimliği yalnız token'dan gelir; istemci aday kimliği veremez. Yanıt kişisel veri içerdiğinden
 * önbelleğe alınmaz.
 */
@RestController
@RequiredArgsConstructor
public class RecommendationCandidateController {

    /** Aday keşfi kapsamı; hesaba bağlıdır. */
    public static final String CANDIDATES_READ_SCOPE = "candidates:read";
    /** Aday keşfini yalnız Recommendation isteyebilir. */
    public static final String CANDIDATES_READ_SUBJECT = "svc:recommendation-service";

    private final RecommendationCandidateService candidates;

    @GetMapping(InternalApiPaths.RECOMMENDATION_CANDIDATES)
    public ResponseEntity<RecommendationCandidatesResponse> candidates(@PathVariable UUID accountId,
            @RequestParam(required = false) Integer limit, @RequestParam(required = false) UUID cursor,
            @RequestParam(required = false) String genre, @RequestParam(required = false) String platform,
            @RequestParam(defaultValue = "false") boolean excludeFriends,
            @AuthenticationPrincipal Jwt token) {
        if (!CANDIDATES_READ_SUBJECT.equals(token.getSubject())
                || !accountId.toString().equals(token.getClaimAsString(TokenClaims.ACCOUNT_ID))) {
            throw new AccessDeniedException("Service token is not valid for this candidate read");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(RecommendationCandidatesResponse.from(candidates.candidates(accountId, limit, cursor, genre, platform, excludeFriends)));
    }
}
