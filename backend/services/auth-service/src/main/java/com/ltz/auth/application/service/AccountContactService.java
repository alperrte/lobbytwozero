package com.ltz.auth.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.auth.domain.exception.AuthErrorCode;
import com.ltz.auth.domain.exception.AuthException;
import com.ltz.auth.infrastructure.persistence.repository.AccountRepository;

import lombok.RequiredArgsConstructor;

/** Mail Service için hesap iletişim adresi çözümü (yalnız okuma, hiçbir şey loglanmaz). */
@Service
@RequiredArgsConstructor
public class AccountContactService {

    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public AccountContactView get(UUID accountId) {
        return accountRepository.findById(accountId).map(AccountContactView::of)
                .orElseThrow(() -> new AuthException(AuthErrorCode.ACCOUNT_NOT_FOUND));
    }
}
