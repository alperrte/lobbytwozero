package com.ltz.game.api.controller;

import com.ltz.game.api.dto.request.RecommendationMetadataBatchRequest;
import com.ltz.game.api.dto.response.RecommendationMetadataBatchResponse;
import com.ltz.game.application.service.RecommendationMetadataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Yalnız Recommendation → Game. Bu yol Gateway'e yönlendirilmez. */
@RestController @RequiredArgsConstructor
public class RecommendationMetadataController {
    public static final String PATH = "/internal/v1/recommendation/game-metadata/batch";
    private final RecommendationMetadataService service;

    @PostMapping(PATH)
    public ResponseEntity<RecommendationMetadataBatchResponse> batch(@AuthenticationPrincipal Jwt token,
            @Valid @RequestBody RecommendationMetadataBatchRequest request) {
        if (!"svc:recommendation-service".equals(token.getSubject()) || !(token.getClaims().get("account_id") instanceof String account)
                || account.isBlank())
            throw new AccessDeniedException("Servis token'ı hesaba bağlı değil veya çağıran geçersiz.");
        return ResponseEntity.ok().header("Cache-Control", "private, no-store").body(service.batch(request.gameIds()));
    }
}
