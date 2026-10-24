package com.ltz.game.application.service;

import com.ltz.game.domain.exception.AccountErasurePendingException;
import com.ltz.game.infrastructure.persistence.repository.AccountErasureRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountErasureService {
    private final AccountErasureRepository repository;

    public AccountErasureService(AccountErasureRepository repository) { this.repository = repository; }

    @Transactional(timeout = 3)
    public void erase(UUID accountId) {
        if (accountId == null || accountId.equals(new UUID(0, 0)))
            throw new IllegalArgumentException("Geçersiz hesap kimliği.");
        try {
            repository.erase(accountId);
            // Hata/eksik temizlik transaction'ı geri alır; kişisel veri varken 204 verilmez.
            if (repository.hasAccountData(accountId)) throw new AccountErasurePendingException();
        } catch (org.springframework.dao.TransientDataAccessException _) {
            throw new AccountErasurePendingException();
        }
    }
}
