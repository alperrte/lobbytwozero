package com.ltz.auth.api.exception;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.PayloadTooLargeException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.ltz.auth.domain.exception.AuthErrorCode;
import com.ltz.auth.domain.exception.AuthException;
import com.ltz.auth.domain.exception.EmailVerificationFailedException;
import com.ltz.auth.domain.exception.PasswordHashingCapacityExceededException;
import com.ltz.auth.domain.exception.UsernameAlreadyTakenException;

import lombok.extern.slf4j.Slf4j;

/**
 * Tüm hatalar RFC 9457 ProblemDetail döner. Gövdeye istek değeri, stack trace veya SQL yazılmaz;
 * doğrulama hatalarında yalnız alan adı ve kural kodu bulunur (parola değeri asla geri yansıtılmaz).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    static final String CODE_PROPERTY = "code";
    static final String ERRORS_PROPERTY = "errors";
    private static final Duration ONE_SECOND = Duration.ofSeconds(1);

    @ExceptionHandler(UsernameAlreadyTakenException.class)
    ProblemDetail handleUsernameTaken() {
        return problem(HttpStatus.CONFLICT, "USERNAME_TAKEN", "Username is already taken.");
    }

    @ExceptionHandler(EmailVerificationFailedException.class)
    ProblemDetail handleVerificationFailed() {
        return problem(HttpStatus.BAD_REQUEST, "VERIFICATION_FAILED",
                "The verification code is invalid or has expired. Request a new code.");
    }

    @ExceptionHandler(AuthException.class)
    ResponseEntity<ProblemDetail> handleAuth(AuthException exception) {
        AuthErrorCode errorCode = exception.errorCode();
        HttpStatus status = HttpStatus.valueOf(errorCode.httpStatus());
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        exception.retryAfter().ifPresent(retryAfter ->
                response.header(HttpHeaders.RETRY_AFTER, String.valueOf(roundUpSeconds(retryAfter))));
        return response.body(problem(status, errorCode.name(), errorCode.detail()));
    }

    @ExceptionHandler(CsrfException.class)
    ProblemDetail handleCsrf() {
        return problem(HttpStatus.FORBIDDEN, "CSRF_TOKEN_INVALID", "CSRF token is missing or invalid.");
    }

    @ExceptionHandler(PasswordHashingCapacityExceededException.class)
    ProblemDetail handleHashingCapacity() {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_BUSY",
                "The service is temporarily busy. Try again shortly.");
    }

    @ExceptionHandler(PayloadTooLargeException.class)
    ResponseEntity<ProblemDetail> handlePayloadTooLarge() {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(payloadTooLarge());
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleAuthentication() {
        return problem(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied() {
        return problem(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access to this resource is denied.");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        log.error("Unhandled exception", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed.");
        List<Map<String, String>> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::fieldError)
                .toList();
        body.setProperty(ERRORS_PROPERTY, errors);
        return ResponseEntity.badRequest().body(body);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        // Chunked gövdede sınır okuma sırasında aşılırsa JSON ayrıştırıcısı hatayı sarmalayarak iletir.
        if (hasCause(exception, PayloadTooLargeException.class)) {
            return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(payloadTooLarge());
        }
        return ResponseEntity.badRequest()
                .body(problem(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body could not be read."));
    }

    /** Retry-After tam saniyedir; kalan süre yukarı yuvarlanır ve en az 1 sn olur. */
    private static long roundUpSeconds(Duration duration) {
        return Math.max(1, duration.plus(ONE_SECOND).minusNanos(1).toSeconds());
    }

    private static ProblemDetail payloadTooLarge() {
        return problem(HttpStatus.CONTENT_TOO_LARGE, "PAYLOAD_TOO_LARGE", "Request body is too large.");
    }

    private static Map<String, String> fieldError(FieldError error) {
        return Map.of("field", error.getField(), CODE_PROPERTY, String.valueOf(error.getCode()));
    }

    private static boolean hasCause(Throwable exception, Class<? extends Throwable> type) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty(CODE_PROPERTY, code);
        return problem;
    }
}
