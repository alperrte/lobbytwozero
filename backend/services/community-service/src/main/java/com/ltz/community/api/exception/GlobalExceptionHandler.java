package com.ltz.community.api.exception;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.ltz.community.domain.exception.AuthenticationRequiredException;
import com.ltz.community.domain.exception.GameCatalogUnavailableException;
import com.ltz.community.domain.exception.GameNotAvailableException;
import com.ltz.community.domain.exception.InvalidImageException;
import com.ltz.community.domain.exception.PostAccessDeniedException;
import com.ltz.community.domain.exception.PostImageLimitExceededException;
import com.ltz.community.domain.exception.PostImageNotFoundException;
import com.ltz.community.domain.exception.PostNotEditableException;
import com.ltz.community.domain.exception.PostNotFoundException;

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

	@ExceptionHandler(PostNotFoundException.class)
	public ProblemDetail handlePostNotFound(PostNotFoundException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
	}

	@ExceptionHandler(PostAccessDeniedException.class)
	public ProblemDetail handlePostAccessDenied(PostAccessDeniedException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
	}

	@ExceptionHandler(PostNotEditableException.class)
	public ProblemDetail handlePostNotEditable(PostNotEditableException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
	}

	@ExceptionHandler(GameNotAvailableException.class)
	public ProblemDetail handleGameNotAvailable(GameNotAvailableException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage());
	}

	@ExceptionHandler(GameCatalogUnavailableException.class)
	public ProblemDetail handleGameCatalogUnavailable(GameCatalogUnavailableException exception) {
		log.warn("Game catalog could not be reached: {}", exception.getCause().getClass().getSimpleName());
		return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
	}

	@ExceptionHandler(PostImageNotFoundException.class)
	public ProblemDetail handlePostImageNotFound(PostImageNotFoundException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
	}

	@ExceptionHandler(PostImageLimitExceededException.class)
	public ProblemDetail handlePostImageLimitExceeded(PostImageLimitExceededException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
	}

	@ExceptionHandler(InvalidImageException.class)
	public ProblemDetail handleInvalidImage(InvalidImageException exception) {
		HttpStatus status = switch (exception.getReason()) {
			case EMPTY -> HttpStatus.BAD_REQUEST;
			case UNSUPPORTED_TYPE -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
			case TOO_LARGE -> HttpStatus.CONTENT_TOO_LARGE;
			case CORRUPT, DIMENSIONS_EXCEEDED -> HttpStatus.UNPROCESSABLE_CONTENT;
		};
		return ProblemDetail.forStatusAndDetail(status, exception.getMessage());
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
