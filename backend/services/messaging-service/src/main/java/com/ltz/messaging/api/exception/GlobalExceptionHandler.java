package com.ltz.messaging.api.exception;

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
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ContentTooLargeException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.ltz.messaging.domain.exception.MessagingErrorCode;
import com.ltz.messaging.domain.exception.MessagingException;

import lombok.extern.slf4j.Slf4j;

/**
 * Tüm hatalar RFC 9457 ProblemDetail + {@code code} döner. Gövdeye gönderilen değer, istenen yol, stack trace, SQL ya da
 * mesaj içeriği yazılmaz; doğrulama hatalarında yalnız alan adı ve kural kodu bulunur.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE";
    private static final String TOO_LARGE_DETAIL = "Request body is too large.";
    private static final String MALFORMED_REQUEST = "MALFORMED_REQUEST";

    @ExceptionHandler(MessagingException.class)
    ResponseEntity<ProblemDetail> handleMessaging(MessagingException exception) {
        return error(exception.errorCode());
    }

    /** Sohbet satırı başka bir işlem tarafından değiştirildi (@Version); istemci tekrar deneyebilir. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleConcurrentModification() {
        return error(MessagingErrorCode.CONCURRENT_MODIFICATION);
    }

    @ExceptionHandler(ContentTooLargeException.class)
    ResponseEntity<ProblemDetail> handleContentTooLarge() {
        return respond(HttpStatus.CONTENT_TOO_LARGE, PAYLOAD_TOO_LARGE, TOO_LARGE_DETAIL);
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
        List<Map<String, String>> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of("field", error.getField(), "code", String.valueOf(error.getCode())))
                .toList();
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed.");
        body.setProperty("errors", violations);
        return ResponseEntity.badRequest().body(body);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        if (NestedExceptionUtils.getMostSpecificCause(exception) instanceof ContentTooLargeException) {
            return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                    .body(problem(HttpStatus.CONTENT_TOO_LARGE, PAYLOAD_TOO_LARGE, TOO_LARGE_DETAIL));
        }
        return ResponseEntity.badRequest()
                .body(problem(HttpStatus.BAD_REQUEST, MALFORMED_REQUEST, "Request body could not be read."));
    }

    /** Geçersiz path/query değeri (ör. UUID olmayan conversationId) geri yansıtılmadan 400 döner. */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException exception, HttpHeaders headers,
                                                        HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest()
                .body(problem(HttpStatus.BAD_REQUEST, MALFORMED_REQUEST, "A request parameter is invalid."));
    }

    /** Spring'in varsayılan 404 gövdesi istenen yolu geri yansıtır; sabit metin döner. */
    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException exception,
                                                                    HttpHeaders headers, HttpStatusCode status,
                                                                    WebRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(problem(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource was not found."));
    }

    private static ResponseEntity<ProblemDetail> error(MessagingErrorCode errorCode) {
        return respond(HttpStatus.valueOf(errorCode.httpStatus()), errorCode.name(), errorCode.detail());
    }

    private static ResponseEntity<ProblemDetail> respond(HttpStatus status, String code, String detail) {
        return ResponseEntity.status(status).body(problem(status, code, detail));
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
