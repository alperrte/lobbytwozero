package com.ltz.review.api.controller;

import com.ltz.review.api.dto.request.RatingSummariesRequest;
import com.ltz.review.api.dto.response.RatingSummariesResponse;
import com.ltz.review.api.dto.response.RatingSummaryPageResponse;
import com.ltz.review.application.service.RatingSummaryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Yalnız Game → Review (svc+jwt, scope rating-summaries:read). Bu yol Gateway'e yönlendirilmez. */
@RestController
public class InternalRatingSummaryController {
    public static final String PATH = "/internal/v1/games/rating-summaries";
    public static final String SCOPE = "rating-summaries:read";
    static final String CALLER = "svc:game-service";
    public static final int MAX_PAGE = 500;
    private final RatingSummaryService service;

    public InternalRatingSummaryController(RatingSummaryService service) { this.service = service; }

    @PostMapping(PATH)
    public RatingSummariesResponse getAll(@Valid @RequestBody RatingSummariesRequest request,
            @AuthenticationPrincipal Jwt token) {
        requireCaller(token);
        return service.getAll(request.gameIds());
    }

    /** Game'in puan projeksiyonu için: puanı olan tüm oyunlar, gameId artan imleçli sayfalar. */
    @GetMapping(PATH)
    public RatingSummaryPageResponse page(@AuthenticationPrincipal Jwt token,
            @RequestParam(required = false) UUID after,
            @RequestParam(defaultValue = "200") @Min(1) @Max(MAX_PAGE) int limit) {
        requireCaller(token);
        return service.page(after, limit);
    }

    private static void requireCaller(Jwt token) {
        if (!CALLER.equals(token.getSubject()))
            throw new AccessDeniedException("Servis token'ı bu okuma işlemi için geçerli değil.");
    }
}
