package com.ltz.community.api.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.hibernate.TransactionException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionSystemException;

/**
 * Veritabanı bağlantı kaybı ile diğer transaction/JPA hatalarının ayrımı: yalnız kanıtlı bağlantı kaybı 503, geri kalanı
 * 500 döner; hata zinciri boş, derin ya da döngülü olsa da yanıt üretilir.
 */
class GlobalExceptionHandlerTest {

	private static final String UNAVAILABLE_DETAIL = "The service is temporarily unavailable. Try again later.";
	private static final String UNEXPECTED_DETAIL = "An unexpected error occurred.";

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	// ------------------------------------------------------------------ TransactionSystemException

	@Test
	void transactionFailureWithoutAnyCauseStaysInternalServerError() {
		// getOriginalException() null döner; null zincir bağlantı kaybı sayılmaz ve yanıt yine üretilir.
		TransactionSystemException failure = new TransactionSystemException("commit failed");

		assertThat(failure.getOriginalException()).isNull();
		assertInternalServerError(handler.handleTransactionFailure(failure));
	}

	@Test
	void transactionFailureWithAnUnrelatedApplicationErrorStaysInternalServerError() {
		TransactionSystemException failure = new TransactionSystemException("commit failed");
		failure.initApplicationException(new IllegalStateException("business rule"));

		assertInternalServerError(handler.handleTransactionFailure(failure));
	}

	@Test
	void transactionFailureWrappingAConnectionLossInItsCauseIsServiceUnavailable() {
		TransactionSystemException failure = new TransactionSystemException("commit failed",
				new DataAccessResourceFailureException("pool exhausted"));

		assertServiceUnavailable(handler.handleTransactionFailure(failure));
	}

	@Test
	void transactionFailureWhoseApplicationErrorIsAConnectionLossIsServiceUnavailable() {
		TransactionSystemException failure = new TransactionSystemException("commit failed");
		failure.initApplicationException(new CannotCreateTransactionException("no connection"));

		assertServiceUnavailable(handler.handleTransactionFailure(failure));
	}

	@Test
	void transactionFailureCausedByAConnectionSqlStateIsServiceUnavailable() {
		TransactionSystemException failure = new TransactionSystemException("commit failed",
				new SQLException("link down", "08006"));

		assertServiceUnavailable(handler.handleTransactionFailure(failure));
	}

	// ------------------------------------------------------------------ JpaSystemException

	@Test
	void jpaFailureWithoutAConnectionSignalStaysInternalServerError() {
		JpaSystemException failure = new JpaSystemException(new RuntimeException("mapping"));

		assertInternalServerError(handler.handleJpaFailure(failure));
	}

	@Test
	void jpaFailureWithANonConnectionSqlStateStaysInternalServerError() {
		JpaSystemException failure = new JpaSystemException(new RuntimeException(new SQLException("syntax", "42601")));

		assertInternalServerError(handler.handleJpaFailure(failure));
	}

	@Test
	void jpaFailureWithAConnectionSqlStateDeepInTheCauseChainIsServiceUnavailable() {
		JpaSystemException failure = new JpaSystemException(
				new RuntimeException(new IllegalStateException(new SQLException("link down", "08003"))));

		assertServiceUnavailable(handler.handleJpaFailure(failure));
	}

	@Test
	void jpaFailureWithAQueryTimeoutCauseIsServiceUnavailable() {
		JpaSystemException failure = new JpaSystemException(new QueryTimeoutException("statement timeout"));

		assertServiceUnavailable(handler.handleJpaFailure(failure));
	}

	@Test
	void rollbackFailedBecauseHikariClosedTheConnectionIsServiceUnavailable() {
		SQLException closed = new SQLException("Connection is closed");
		JpaSystemException failure = new JpaSystemException(new TransactionException("Unable to rollback", closed));

		assertServiceUnavailable(handler.handleJpaFailure(failure));
	}

	@Test
	void rollbackFailureWithAnotherSqlMessageStaysInternalServerError() {
		JpaSystemException failure = new JpaSystemException(
				new TransactionException("Unable to rollback", new SQLException("Deadlock detected")));

		assertInternalServerError(handler.handleJpaFailure(failure));
	}

	@Test
	void closedConnectionMessageWithASqlStateIsNotTreatedAsTheHikariRollbackCase() {
		JpaSystemException failure = new JpaSystemException(
				new TransactionException("Unable to rollback", new SQLException("Connection is closed", "XX000")));

		assertInternalServerError(handler.handleJpaFailure(failure));
	}

	@Test
	void rollbackFailureWithoutAnUnderlyingCauseStaysInternalServerError() {
		JpaSystemException failure = new JpaSystemException(new TransactionException("Unable to rollback"));

		assertInternalServerError(handler.handleJpaFailure(failure));
	}

	// ------------------------------------------------------------------ zincir sınırı

	@Test
	void cyclicCauseChainTerminatesAndStaysInternalServerError() {
		RuntimeException first = new RuntimeException("first");
		RuntimeException second = new RuntimeException("second");
		first.initCause(second);
		second.initCause(first);

		assertInternalServerError(handler.handleJpaFailure(new JpaSystemException(first)));
	}

	@Test
	void connectionLossBeyondTheScanLimitIsNotDetected() {
		Throwable chain = new SQLException("link down", "08006");
		for (int depth = 0; depth < 32; depth++) {
			chain = new RuntimeException("wrapper " + depth, chain);
		}
		// JpaSystemException + 32 sarmalayıcı: bağlantı hatası 34. halkada kalır ve taramanın dışındadır.
		assertInternalServerError(handler.handleJpaFailure(new JpaSystemException((RuntimeException) chain)));
	}

	@Test
	void connectionLossAtTheLastScannedLinkIsStillDetected() {
		Throwable chain = new SQLException("link down", "08006");
		for (int depth = 0; depth < 30; depth++) {
			chain = new RuntimeException("wrapper " + depth, chain);
		}
		// JpaSystemException + 30 sarmalayıcı + SQLException = 32 halka.
		assertServiceUnavailable(handler.handleJpaFailure(new JpaSystemException((RuntimeException) chain)));
	}

	private static void assertServiceUnavailable(ProblemDetail problem) {
		assertThat(problem.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
		assertThat(problem.getDetail()).isEqualTo(UNAVAILABLE_DETAIL);
	}

	private static void assertInternalServerError(ProblemDetail problem) {
		assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
		assertThat(problem.getDetail()).isEqualTo(UNEXPECTED_DETAIL);
	}
}
