package com.ltz.performance.api.exception;

import com.ltz.performance.domain.exception.AuthServiceUnavailableException;
import com.ltz.performance.domain.exception.PerformanceConflictException;
import com.ltz.performance.domain.exception.PerformanceForbiddenException;
import com.ltz.performance.domain.exception.PerformanceNotFoundException;
import com.ltz.performance.domain.exception.PerformanceRateLimitException;
import com.ltz.performance.domain.exception.PerformanceUnprocessableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Tüm hatalar {@code ProblemDetail} + sabit {@code code} alanıyla döner; iç ayrıntı ve kimlik sızdırılmaz. */
@RestControllerAdvice
public class PerformanceExceptionHandler extends ResponseEntityExceptionHandler {
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return problem(status.value(), "INVALID_REQUEST", "İstek doğrulanamadı.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Object> invalidInput(IllegalArgumentException exception) {
        return problem(400, "INVALID_REQUEST", "İstek doğrulanamadı.");
    }

    @ExceptionHandler({DataIntegrityViolationException.class, ObjectOptimisticLockingFailureException.class})
    ResponseEntity<Object> dataConflict(Exception exception) {
        return problem(409, "DATA_CONFLICT", "Kayıt değişti veya veri kuralıyla çakışıyor.");
    }

    @ExceptionHandler(PerformanceConflictException.class)
    ResponseEntity<Object> conflict(PerformanceConflictException exception) {
        return problem(409, exception.code(), "İşlem mevcut kayıtla çakışıyor.");
    }

    @ExceptionHandler(PerformanceForbiddenException.class)
    ResponseEntity<Object> forbidden(PerformanceForbiddenException exception) {
        return problem(403, exception.code(), "Bu işlem için gerekli koşullar sağlanmıyor.");
    }

    @ExceptionHandler(PerformanceUnprocessableException.class)
    ResponseEntity<Object> unprocessable(PerformanceUnprocessableException exception) {
        return problem(422, exception.code(), "İstek içeriği işlenemedi.");
    }

    @ExceptionHandler(PerformanceNotFoundException.class)
    ResponseEntity<Object> notFound(PerformanceNotFoundException exception) {
        return problem(404, "PERFORMANCE_NOT_FOUND", "İstenen kaynak bulunamadı.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Object> denied(AccessDeniedException exception) {
        return problem(403, "ACCESS_DENIED", "Bu işlem için erişim reddedildi.");
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Object> unauthenticated(AuthenticationException exception) {
        return problem(401, "AUTHENTICATION_REQUIRED", "Doğrulanmış kimlik gerekli.");
    }

    @ExceptionHandler(PerformanceRateLimitException.class)
    ResponseEntity<Object> limited(PerformanceRateLimitException exception) {
        return withRetryAfter(problem(429, "RATE_LIMITED", "İşlem sınırı aşıldı; daha sonra tekrar deneyin."), "60");
    }

    @ExceptionHandler(AuthServiceUnavailableException.class)
    ResponseEntity<Object> authUnavailable(AuthServiceUnavailableException exception) {
        return withRetryAfter(problem(503, "AUTH_UNAVAILABLE", "Kimlik doğrulama servisi şu an doğrulama yapamıyor."), "30");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpected(Exception exception) {
        return problem(500, "INTERNAL_ERROR", "İşlem tamamlanamadı.");
    }

    private static ResponseEntity<Object> withRetryAfter(ResponseEntity<Object> result, String seconds) {
        return ResponseEntity.status(result.getStatusCode()).headers(result.getHeaders())
                .header(HttpHeaders.RETRY_AFTER, seconds).body(result.getBody());
    }

    private static ResponseEntity<Object> problem(int status, String code, String detail) {
        var body = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
        body.setProperty("code", code);
        var response = ResponseEntity.status(status).header(HttpHeaders.CACHE_CONTROL, "private, no-store");
        if (status == 401) response.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return response.body(body);
    }
}
