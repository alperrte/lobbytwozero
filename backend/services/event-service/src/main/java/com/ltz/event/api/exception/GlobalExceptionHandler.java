package com.ltz.event.api.exception;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.ltz.event.domain.exception.AuthenticationRequiredException;
import com.ltz.event.domain.exception.EventCreationForbiddenException;
import com.ltz.event.domain.exception.InvalidEventScheduleException;

/** Tüm hatalar ProblemDetail (RFC 9457) döner; stack trace, SQL veya kullanıcı girdisi yanıta yazılmaz. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldViolation> violations = exception.getBindingResult().getFieldErrors().stream()
				.map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
				.toList();
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed.");
		problem.setTitle("Validation Failed");
		problem.setProperty("violations", violations);
		return handleExceptionInternal(exception, problem, headers, status, request);
	}

	@ExceptionHandler(AuthenticationRequiredException.class)
	public ProblemDetail handleAuthenticationRequired(AuthenticationRequiredException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

	@ExceptionHandler(EventCreationForbiddenException.class)
	public ProblemDetail handleEventCreationForbidden(EventCreationForbiddenException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
	}

	@ExceptionHandler(InvalidEventScheduleException.class)
	public ProblemDetail handleInvalidSchedule(InvalidEventScheduleException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
		problem.setTitle("Invalid Event Schedule");
		problem.setProperty("reason", exception.getReason().name());
		return problem;
	}

	/**
	 * Veritabanı geçici olarak erişilemezse (bağlantı havuzu/zaman aşımı) 503 döner; hata 500'e çevrilmez. Ayrıntı (SQL,
	 * bağlantı bilgisi) yanıta yazılmaz, yalnız istisna türü loglanır.
	 */
	@ExceptionHandler({ DataAccessResourceFailureException.class, CannotCreateTransactionException.class,
			QueryTimeoutException.class })
	public ProblemDetail handleDatabaseUnavailable(Exception exception) {
		log.warn("Database is temporarily unavailable: {}", exception.getClass().getSimpleName());
		return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
				"The service is temporarily unavailable. Try again later.");
	}

	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	public ProblemDetail handleConcurrentModification(ObjectOptimisticLockingFailureException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"The resource was modified concurrently. Reload and retry.");
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ProblemDetail handleAccessDenied(AccessDeniedException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access is denied.");
	}

	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpected(Exception exception) {
		log.error("Unexpected error while handling request", exception);
		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
	}

	public record FieldViolation(String field, String message) {
	}
}
