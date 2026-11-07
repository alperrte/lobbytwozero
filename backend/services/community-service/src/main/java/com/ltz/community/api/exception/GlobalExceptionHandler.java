package com.ltz.community.api.exception;

import java.sql.SQLException;
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
import org.springframework.orm.jpa.JpaSystemException;
import org.hibernate.TransactionException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.ltz.community.domain.exception.AlreadyReportedException;
import com.ltz.community.domain.exception.AlreadyRepostedException;
import com.ltz.community.domain.exception.AuthenticationRequiredException;
import com.ltz.community.domain.exception.CommentAccessDeniedException;
import com.ltz.community.domain.exception.CommentNotFoundException;
import com.ltz.community.domain.exception.ForumGameMismatchException;
import com.ltz.community.domain.exception.ForumNotFoundException;
import com.ltz.community.domain.exception.ForumSlugTakenException;
import com.ltz.community.domain.exception.GameContentRuleException;
import com.ltz.community.domain.exception.GameCatalogUnavailableException;
import com.ltz.community.domain.exception.GameNotAvailableException;
import com.ltz.community.domain.exception.InvalidImageException;
import com.ltz.community.domain.exception.InvalidInboundEventException;
import com.ltz.community.domain.exception.InvalidPostQueryException;
import com.ltz.community.domain.exception.ModeratorRequiredException;
import com.ltz.community.domain.exception.PostAccessDeniedException;
import com.ltz.community.domain.exception.PostImageLimitExceededException;
import com.ltz.community.domain.exception.PostImageNotFoundException;
import com.ltz.community.domain.exception.PostNotEditableException;
import com.ltz.community.domain.exception.PostNotFoundException;
import com.ltz.community.domain.exception.ReportingOwnContentException;
import com.ltz.community.domain.exception.TooManyGameContentException;
import com.ltz.community.domain.exception.TooManyReportsException;

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

	@ExceptionHandler(CommentNotFoundException.class)
	public ProblemDetail handleCommentNotFound(CommentNotFoundException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
	}

	@ExceptionHandler(CommentAccessDeniedException.class)
	public ProblemDetail handleCommentAccessDenied(CommentAccessDeniedException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
	}

	@ExceptionHandler(AlreadyReportedException.class)
	public ProblemDetail handleAlreadyReported(AlreadyReportedException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
	}

	@ExceptionHandler(ReportingOwnContentException.class)
	public ProblemDetail handleReportingOwnContent(ReportingOwnContentException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage());
	}

	@ExceptionHandler(TooManyReportsException.class)
	public ResponseEntity<ProblemDetail> handleTooManyReports(TooManyReportsException exception) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "3600")
				.body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage()));
	}

	@ExceptionHandler(TooManyGameContentException.class)
	public ResponseEntity<ProblemDetail> handleTooManyGameContent(TooManyGameContentException exception) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "3600")
				.body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage()));
	}

	@ExceptionHandler(GameContentRuleException.class)
	public ProblemDetail handleGameContentRule(GameContentRuleException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage());
	}

	/**
	 * Veritabanı geçici olarak erişilemezse (bağlantı havuzu/zaman aşımı) 503 döner; hata boş sonuca ya da 500'e
	 * çevrilmez. Ayrıntı (SQL, bağlantı bilgisi) yanıta yazılmaz, yalnız istisna türü loglanır.
	 */
	@ExceptionHandler({ DataAccessResourceFailureException.class, CannotCreateTransactionException.class,
			QueryTimeoutException.class })
	public ProblemDetail handleDatabaseUnavailable(Exception exception) {
		log.warn("Database is temporarily unavailable: {}", exception.getClass().getSimpleName());
		return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
				"The service is temporarily unavailable. Try again later.");
	}

	/** Rollback hatası ilk bağlantı hatasını sarmalayabilir; başka transaction hataları 500 kalır. */
	@ExceptionHandler(TransactionSystemException.class)
	public ProblemDetail handleTransactionFailure(TransactionSystemException exception) {
		if (isConnectionFailure(exception) || isConnectionFailure(exception.getOriginalException())) {
			return handleDatabaseUnavailable(exception);
		}
		return handleUnexpected(exception);
	}

	@ExceptionHandler(JpaSystemException.class)
	public ProblemDetail handleJpaFailure(JpaSystemException exception) {
		if (isConnectionFailure(exception) || isClosedConnectionRollback(exception)) {
			return handleDatabaseUnavailable(exception);
		}
		return handleUnexpected(exception);
	}

	private static boolean isClosedConnectionRollback(JpaSystemException exception) {
		// Hikari'nin kapalı bağlantı vekili SQLException'a SQLState eklemez. Yalnız Hibernate
		// rollback sarmalayıcısındaki bu sabit hata kabul edilir; başka SQL hataları 500 kalır.
		return exception.getCause() instanceof TransactionException rollback
				&& rollback.getCause() instanceof SQLException sql
				&& sql.getSQLState() == null && "Connection is closed".equals(sql.getMessage());
	}

	private static boolean isConnectionFailure(Throwable failure) {
		Throwable current = failure;
		// Bozuk/döngülü cause zinciri hata yanıtını kilitlememeli.
		for (int depth = 0; current != null && depth < 32; depth++) {
			if (current instanceof DataAccessResourceFailureException
					|| current instanceof CannotCreateTransactionException || current instanceof QueryTimeoutException) {
				return true;
			}
			if (current instanceof SQLException sql && sql.getSQLState() != null && sql.getSQLState().startsWith("08")) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	@ExceptionHandler(InvalidInboundEventException.class)
	public ProblemDetail handleInvalidInboundEvent(InvalidInboundEventException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
	}

	@ExceptionHandler(AlreadyRepostedException.class)
	public ProblemDetail handleAlreadyReposted(AlreadyRepostedException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
	}

	@ExceptionHandler(ForumNotFoundException.class)
	public ProblemDetail handleForumNotFound(ForumNotFoundException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
	}

	@ExceptionHandler(ForumSlugTakenException.class)
	public ProblemDetail handleForumSlugTaken(ForumSlugTakenException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
	}

	@ExceptionHandler(ForumGameMismatchException.class)
	public ProblemDetail handleForumGameMismatch(ForumGameMismatchException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage());
	}

	@ExceptionHandler(ModeratorRequiredException.class)
	public ProblemDetail handleModeratorRequired(ModeratorRequiredException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
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

	@ExceptionHandler(InvalidPostQueryException.class)
	public ProblemDetail handleInvalidPostQuery(InvalidPostQueryException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
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
