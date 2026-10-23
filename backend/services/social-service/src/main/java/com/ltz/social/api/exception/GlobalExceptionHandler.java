package com.ltz.social.api.exception;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.TypeMismatchException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ContentTooLargeException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.ltz.social.domain.exception.SocialErrorCode;
import com.ltz.social.domain.exception.SocialException;

import lombok.extern.slf4j.Slf4j;

/**
 * Tüm hatalar RFC 9457 ProblemDetail döner. Gövdeye istek değeri, istenen yol, stack trace veya SQL yazılmaz;
 * doğrulama hatalarında yalnız alan adı ve kural kodu bulunur.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String CODE = "code";

    @ExceptionHandler(SocialException.class)
    ResponseEntity<ProblemDetail> handleSocial(SocialException exception) {
        SocialErrorCode errorCode = exception.errorCode();
        return respond(HttpStatus.valueOf(errorCode.httpStatus()), errorCode.name(), errorCode.detail());
    }

    /** İstek satırı başka bir işlem tarafından değiştirildi (@Version); istemci tekrar deneyebilir. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleConcurrentModification() {
        SocialErrorCode errorCode = SocialErrorCode.CONCURRENT_MODIFICATION;
        return respond(HttpStatus.CONFLICT, errorCode.name(), errorCode.detail());
    }

    @ExceptionHandler(ContentTooLargeException.class)
    ResponseEntity<ProblemDetail> handleContentTooLarge() {
        return respond(HttpStatus.CONTENT_TOO_LARGE, "PAYLOAD_TOO_LARGE", "Request body is too large.");
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication() {
        return respond(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied() {
        return respond(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access to this resource is denied.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        log.error("Unhandled exception", exception);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        List<Map<String, String>> violations = new ArrayList<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            violations.add(Map.of("field", error.getField(), CODE, String.valueOf(error.getCode())));
        }
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed.");
        body.setProperty("errors", violations);
        return ResponseEntity.badRequest().body(body);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        // Chunked gövdede sınır okuma sırasında aşılırsa JSON ayrıştırıcısı hatayı sarmalar; asıl nedene bakılır.
        if (NestedExceptionUtils.getMostSpecificCause(exception) instanceof ContentTooLargeException) {
            return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                    .body(problem(HttpStatus.CONTENT_TOO_LARGE, "PAYLOAD_TOO_LARGE", "Request body is too large."));
        }
        return ResponseEntity.badRequest()
                .body(problem(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body could not be read."));
    }

    /** Geçersiz path/query değeri (ör. UUID olmayan requestId) geri yansıtılmadan 400 döner. */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException exception, HttpHeaders headers,
                                                        HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest()
                .body(problem(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "A request parameter is invalid."));
    }

    /** Spring'in varsayılan 404 gövdesi istenen yolu geri yansıtır; sabit metin döner. */
    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException exception,
                                                                    HttpHeaders headers, HttpStatusCode status,
                                                                    WebRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(problem(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource was not found."));
    }

    private static ResponseEntity<ProblemDetail> respond(HttpStatus status, String code, String detail) {
        return ResponseEntity.status(status).body(problem(status, code, detail));
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty(CODE, code);
        return problem;
    }
}
