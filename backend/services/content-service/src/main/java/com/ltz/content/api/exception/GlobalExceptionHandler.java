package com.ltz.content.api.exception;

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

import com.ltz.content.domain.exception.AdministratorRequiredException;
import com.ltz.content.domain.exception.AuthenticationRequiredException;
import com.ltz.content.domain.exception.ContentAccessDeniedException;
import com.ltz.content.domain.exception.ContentAlreadyPublishedException;
import com.ltz.content.domain.exception.ContentImageLimitExceededException;
import com.ltz.content.domain.exception.ContentImageNotFoundException;
import com.ltz.content.domain.exception.ContentNotEditableException;
import com.ltz.content.domain.exception.ContentNotFoundException;
import com.ltz.content.domain.exception.ContentNotPublishedException;
import com.ltz.content.domain.exception.ContentTranslationNotFoundException;
import com.ltz.content.domain.exception.DraftLimitExceededException;
import com.ltz.content.domain.exception.DuplicateContentException;
import com.ltz.content.domain.exception.GameCatalogUnavailableException;
import com.ltz.content.domain.exception.GameNotAvailableException;
import com.ltz.content.domain.exception.InsufficientModerationAuthorityException;
import com.ltz.content.domain.exception.InvalidContentQueryException;
import com.ltz.content.domain.exception.InvalidContentRequestException;
import com.ltz.content.domain.exception.InvalidImageException;
import com.ltz.content.domain.exception.InvalidInboundEventException;
import com.ltz.content.domain.exception.ModeratorRequiredException;
import com.ltz.content.domain.exception.OfficialAnnouncementForbiddenException;
import com.ltz.content.domain.exception.PublishRequirementsNotMetException;
import com.ltz.content.domain.exception.TooManyContentsException;

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

	@ExceptionHandler({ ContentNotFoundException.class, ContentImageNotFoundException.class,
			ContentTranslationNotFoundException.class })
	public ProblemDetail handleNotFound(RuntimeException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
	}

	@ExceptionHandler({ ContentAccessDeniedException.class, OfficialAnnouncementForbiddenException.class,
			AdministratorRequiredException.class, ModeratorRequiredException.class,
			InsufficientModerationAuthorityException.class })
	public ProblemDetail handleForbidden(RuntimeException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
	}

	@ExceptionHandler({ ContentNotEditableException.class, DuplicateContentException.class,
			DraftLimitExceededException.class, ContentImageLimitExceededException.class,
			ContentAlreadyPublishedException.class, ContentNotPublishedException.class })
	public ProblemDetail handleConflict(RuntimeException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
	}

	@ExceptionHandler({ InvalidContentRequestException.class, InvalidContentQueryException.class,
			InvalidInboundEventException.class })
	public ProblemDetail handleBadRequest(RuntimeException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
	}

	/** Yayın kuralları sağlanmıyor: {@code issues} her eksiği {@code <dil>:<kod>} olarak listeler. */
	@ExceptionHandler(PublishRequirementsNotMetException.class)
	public ProblemDetail handlePublishRequirementsNotMet(PublishRequirementsNotMetException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage());
		problem.setTitle("Publication Requirements Not Met");
		problem.setProperty("issues", exception.getIssues());
		return problem;
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

	@ExceptionHandler(TooManyContentsException.class)
	public ResponseEntity<ProblemDetail> handleTooManyContents(TooManyContentsException exception) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "3600")
				.body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage()));
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
