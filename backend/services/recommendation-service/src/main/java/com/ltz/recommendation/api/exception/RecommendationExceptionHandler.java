package com.ltz.recommendation.api.exception;

import com.ltz.recommendation.domain.exception.RecommendationRequestException;
import com.ltz.recommendation.domain.exception.RecommendationUnavailableException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RecommendationExceptionHandler {
    @ExceptionHandler(RecommendationRequestException.class)
    ResponseEntity<ProblemDetail> request(RecommendationRequestException exception) { return problem(exception.status(), exception.code()); }

    @ExceptionHandler({IllegalArgumentException.class, org.springframework.web.bind.MethodArgumentNotValidException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<ProblemDetail> invalid(Exception exception) { return problem(400, "INVALID_REQUEST"); }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> denied(AccessDeniedException exception) { return problem(403, "ACCESS_DENIED"); }

    @ExceptionHandler({RecommendationUnavailableException.class, org.springframework.dao.DataAccessException.class})
    ResponseEntity<ProblemDetail> unavailable(Exception exception) { return problem(503, "RECOMMENDATION_UNAVAILABLE"); }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception exception) { return problem(500, "INTERNAL_ERROR"); }

    private static ResponseEntity<ProblemDetail> problem(int status, String code) {
        var body = ProblemDetail.forStatus(status);
        body.setProperty("code", code);
        var response = ResponseEntity.status(status).header("Cache-Control", "private, no-store");
        if (status == 429 || status == 503) response.header("Retry-After", "60");
        return response.body(body);
    }
}
