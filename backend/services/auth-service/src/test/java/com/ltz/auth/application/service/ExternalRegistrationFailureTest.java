package com.ltz.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ltz.auth.domain.exception.AuthErrorCode;
import com.ltz.auth.domain.exception.AuthException;
import com.ltz.auth.domain.exception.RedactedCause;
import com.ltz.auth.infrastructure.security.SecureTokens;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class ExternalRegistrationFailureTest {
    @Mock private TransactionTemplate transactions;
    @Mock private SecureTokens secureTokens;
    @Mock private SecurityEventRecorder events;
    @InjectMocks private GoogleLoginService google;
    @InjectMocks private SteamLoginService steam;

    @Test
    void googleConflictPreservesOnlyRedactedDiagnostics() {
        databaseConflict();
        AuthException failure = assertThrows(AuthException.class,
                () -> google.completeRegistration("synthetic-token", "fixture-player"));
        verifyRedacted(failure, AuthErrorCode.ACCOUNT_LINK_REQUIRED);
    }

    @Test
    void steamConflictPreservesOnlyRedactedDiagnostics() {
        databaseConflict();
        AuthException failure = assertThrows(AuthException.class,
                () -> steam.completeRegistration("synthetic-token", "fixture-player"));
        verifyRedacted(failure, AuthErrorCode.REGISTRATION_TOKEN_INVALID);
    }

    private void databaseConflict() {
        var failure = new DataIntegrityViolationException("private-database-row",
                new IllegalStateException("private-driver-detail"));
        when(transactions.execute(any())).thenThrow(failure);
    }

    private static void verifyRedacted(AuthException failure, AuthErrorCode expected) {
        assertThat(failure.errorCode()).isEqualTo(expected);
        assertThat(failure.retryAfter()).isEmpty();
        assertThat(failure.getCause()).isInstanceOf(RedactedCause.class);
        assertThat(failure.getCause().getCause()).isNull();
        var output = new StringWriter();
        failure.printStackTrace(new PrintWriter(output));
        assertThat(output.toString()).contains(DataIntegrityViolationException.class.getName())
                .doesNotContain("private-database-row", "private-driver-detail");
    }
}
