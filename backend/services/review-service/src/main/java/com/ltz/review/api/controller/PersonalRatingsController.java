package com.ltz.review.api.controller;

import com.ltz.review.api.dto.response.PersonalRatingsResponse;
import com.ltz.review.application.service.PersonalRatingsService;
import com.ltz.review.domain.exception.ReviewException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PersonalRatingsController {
    public static final String PATH = "/api/v1/review/ratings/mine";
    private static final Set<String> PARAMETERS = Set.of("after", "size");
    private static final CacheControl PRIVATE = CacheControl.noStore().cachePrivate();
    private final PersonalRatingsService service;

    public PersonalRatingsController(PersonalRatingsService service) { this.service = service; }

    @GetMapping(PATH)
    public ResponseEntity<PersonalRatingsResponse> get(@AuthenticationPrincipal Jwt actor,
            @RequestParam(required = false) String after,
            @RequestParam(defaultValue = "50") @Min(1) @Max(50) int size,
            @RequestParam MultiValueMap<String, String> parameters) {
        if (parameters.entrySet().stream().anyMatch(entry -> !PARAMETERS.contains(entry.getKey()) || entry.getValue().size() != 1)
                || "".equals(parameters.getFirst("size"))) {
            throw new ReviewException(400, "INVALID_RATINGS_QUERY", "Puan sayfası sorgu parametreleri geçersiz.");
        }
        return ResponseEntity.ok().cacheControl(PRIVATE).body(service.get(UUID.fromString(actor.getSubject()), after, size));
    }

    @ExceptionHandler({DataAccessResourceFailureException.class, TransientDataAccessException.class,
            CannotCreateTransactionException.class, TransactionTimedOutException.class})
    ProblemDetail unavailable() {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Kişisel puanlar geçici olarak okunamadı.");
        problem.setProperty("code", "PERSONAL_RATINGS_UNAVAILABLE");
        return problem;
    }
}
