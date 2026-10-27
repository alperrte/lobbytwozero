package com.ltz.hardware.api.exception;

import com.ltz.hardware.domain.exception.HardwareConflictException;
import com.ltz.hardware.domain.exception.HardwareNotFoundException;
import com.ltz.hardware.domain.exception.HardwareRateLimitException;
import com.ltz.hardware.domain.exception.HardwareDeliveryUnavailableException;
import com.ltz.hardware.domain.exception.HardwareSyncCapacityException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class HardwareExceptionHandler extends ResponseEntityExceptionHandler {
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return problem(status.value(), "INVALID_REQUEST", "İstek doğrulanamadı.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Object> invalidInput(IllegalArgumentException exception) {
        return problem(400, "INVALID_REQUEST", "İstek doğrulanamadı.");
    }

    @ExceptionHandler({DataIntegrityViolationException.class, ObjectOptimisticLockingFailureException.class,
            HardwareConflictException.class})
    ResponseEntity<Object> conflict(Exception exception) {
        return problem(409, "DATA_CONFLICT", "Kayıt değişti veya veri kuralıyla çakışıyor.");
    }

    @ExceptionHandler(HardwareNotFoundException.class)
    ResponseEntity<Object> notFound(HardwareNotFoundException exception) {
        return problem(404, "HARDWARE_NOT_FOUND", "İstenen kaynak bulunamadı.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpected(Exception exception) {
        return problem(500, "INTERNAL_ERROR", "İşlem tamamlanamadı.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Object> denied(AccessDeniedException exception) {
        return problem(403, "ACCESS_DENIED", "Bu işlem için erişim reddedildi.");
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Object> unauthenticated(AuthenticationException exception) {
        return problem(401, "AUTHENTICATION_REQUIRED", "Doğrulanmış kimlik gerekli.");
    }

    @ExceptionHandler(HardwareRateLimitException.class)
    ResponseEntity<Object> limited(HardwareRateLimitException exception) {
        var result = problem(429, "RATE_LIMITED", "İşlem sınırı aşıldı; daha sonra tekrar deneyin.");
        return ResponseEntity.status(429).headers(result.getHeaders()).header("Retry-After", "60")
                .body(result.getBody());
    }

    @ExceptionHandler(HardwareDeliveryUnavailableException.class)
    ResponseEntity<Object> unavailable(HardwareDeliveryUnavailableException exception) {
        return problem(503, "MODERATION_DELIVERY_UNAVAILABLE", "Olay kuyruğu sınırı nedeniyle işlem tamamlanamadı.");
    }

    @ExceptionHandler(HardwareSyncCapacityException.class)
    ResponseEntity<Object> syncCapacity(HardwareSyncCapacityException exception) {
        var result = problem(429, "SYNC_RECEIPT_LIMIT_REACHED", "Senkronizasyon kayıt sınırı aşıldı.");
        return ResponseEntity.status(429).headers(result.getHeaders()).header("Retry-After", "3600").body(result.getBody());
    }

    private static ResponseEntity<Object> problem(int status, String code, String detail) {
        var body = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
        body.setProperty("code", code);
        var response = ResponseEntity.status(status).header("Cache-Control", "private, no-store");
        if (status == 401) response.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return response.body(body);
    }
}
