package com.ltz.event.api.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.CannotCreateTransactionException;

import com.ltz.event.domain.exception.AuthenticationRequiredException;
import com.ltz.event.domain.exception.EventCreationForbiddenException;
import com.ltz.event.domain.exception.EventNotFoundException;
import com.ltz.event.domain.exception.InvalidEventScheduleException;
import com.ltz.event.domain.exception.InvalidEventScheduleException.Reason;

/** Hata eşlemeleri: durum kodu doğru, yanıt gövdesi iç ayrıntı (SQL, host, istisna metni) sızdırmaz. */
class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void authenticationRequiredIsMappedTo401() {
		ProblemDetail problem = handler.handleAuthenticationRequired(new AuthenticationRequiredException("gizli"));

		assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
		assertThat(problem.getDetail()).isEqualTo("Authentication is required.");
	}

	@Test
	void eventCreationForbiddenIsMappedTo403() {
		ProblemDetail problem = handler.handleEventCreationForbidden(new EventCreationForbiddenException());

		assertThat(problem.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
		assertThat(problem.getDetail()).isEqualTo("Access is denied.");
	}

	@Test
	void securityAccessDeniedIsMappedTo403() {
		ProblemDetail problem = handler.handleAccessDenied(new AccessDeniedException("ayrıntı"));

		assertThat(problem.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
		assertThat(problem.getDetail()).isEqualTo("Access is denied.");
	}

	@Test
	void invalidScheduleIsMappedTo400WithTheReason() {
		ProblemDetail problem = handler.handleInvalidSchedule(new InvalidEventScheduleException(Reason.ENDS_NOT_AFTER_START));

		assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
		assertThat(problem.getProperties()).containsEntry("reason", "ENDS_NOT_AFTER_START");
	}

	@Test
	void databaseOutagesAreMappedTo503WithoutLeakingDetails() {
		Exception[] outages = { new DataAccessResourceFailureException("jdbc:postgresql://gizli-host/event_db"),
				new CannotCreateTransactionException("havuz tükendi"), new QueryTimeoutException("zaman aşımı") };

		for (Exception outage : outages) {
			ProblemDetail problem = handler.handleDatabaseUnavailable(outage);

			assertThat(problem.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
			assertThat(problem.getDetail()).doesNotContain("gizli").doesNotContain("jdbc");
		}
	}

	@Test
	void optimisticLockFailureIsMappedTo409() {
		ProblemDetail problem = handler.handleConcurrentModification(
				new ObjectOptimisticLockingFailureException(Object.class, "id"));

		assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
	}

	@Test
	void unexpectedErrorsAreMappedTo500WithAGenericMessage() {
		ProblemDetail problem = handler.handleUnexpected(new IllegalStateException("jdbc:postgresql://gizli-host"));

		assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
		assertThat(problem.getDetail()).isEqualTo("An unexpected error occurred.");
	}

	@Test
	void eventNotFoundIsMappedTo404() {
		ProblemDetail problem = handler.handleEventNotFound(new EventNotFoundException());

		assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
		assertThat(problem.getDetail()).isEqualTo("Event not found.");
	}
}
