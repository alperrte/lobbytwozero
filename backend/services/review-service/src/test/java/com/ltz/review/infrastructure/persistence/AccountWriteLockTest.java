package com.ltz.review.infrastructure.persistence;

import com.ltz.review.domain.exception.ReviewException;
import com.ltz.review.infrastructure.persistence.repository.AccountWriteLock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountWriteLockTest {
    @Test void unknownGuardResultFailsClosedInsteadOfUnboxingNull() {
        var jdbc = mock(JdbcClient.class, RETURNS_DEEP_STUBS);
        when(jdbc.sql(anyString()).param(any()).query(Boolean.class).single()).thenReturn(null);
        var accounts = new AccountWriteLock(jdbc);
        UUID user = UUID.randomUUID();
        var exception = assertThrows(ReviewException.class, () -> accounts.requireWritable(user));
        assertEquals(403, exception.status());
    }
}
