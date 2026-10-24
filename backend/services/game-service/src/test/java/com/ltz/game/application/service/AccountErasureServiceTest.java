package com.ltz.game.application.service;

import com.ltz.game.domain.exception.AccountErasurePendingException;
import com.ltz.game.infrastructure.persistence.repository.AccountErasureRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountErasureServiceTest {
    final AccountErasureRepository repository = mock(AccountErasureRepository.class);
    final AccountErasureService service = new AccountErasureService(repository);

    @Test void incompleteErasureCannotBeAcknowledged() {
        UUID account = UUID.randomUUID();
        when(repository.hasAccountData(account)).thenReturn(true);
        assertThrows(AccountErasurePendingException.class, () -> service.erase(account));
    }

    @Test void transientLockFailureRemainsPendingForAuthRetry() {
        UUID account = UUID.randomUUID();
        doThrow(new CannotAcquireLockException("synthetic lock contention")).when(repository).erase(account);
        assertThrows(AccountErasurePendingException.class, () -> service.erase(account));
    }

    @Test void nilOrMissingIdentityCannotTouchAnonymousRecords() {
        UUID nil = new UUID(0, 0);
        assertThrows(IllegalArgumentException.class, () -> service.erase(nil));
        assertThrows(IllegalArgumentException.class, () -> service.erase(null));
        verifyNoInteractions(repository);
    }
}
