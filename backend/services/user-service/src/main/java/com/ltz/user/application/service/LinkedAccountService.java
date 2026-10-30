package com.ltz.user.application.service;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.user.domain.entity.LinkedAccount;
import com.ltz.user.domain.enums.LinkedProvider;
import com.ltz.user.domain.exception.UserErrorCode;
import com.ltz.user.domain.exception.UserException;
import com.ltz.user.infrastructure.persistence.repository.LinkedAccountRepository;

import lombok.RequiredArgsConstructor;

/**
 * Sahibin bağlı hesapları (USER-07/08). Bağlama ve kaldırma Auth'tadır; burada yalnız durum okunur ve açık profilde
 * gösterme izni yönetilir. Kimlik yalnız token'dan gelir.
 */
@Service
@RequiredArgsConstructor
public class LinkedAccountService {

    private final LinkedAccountRepository accounts;
    private final Clock clock;

    /** Her desteklenen sağlayıcı için bir kayıt döner; bağlantı yoksa {@code linked=false}. */
    @Transactional(readOnly = true)
    public List<LinkedAccountView> list(UUID userId) {
        return Arrays.stream(LinkedProvider.values()).map(provider -> view(userId, provider)).toList();
    }

    /**
     * Açık profilde gösterme iznini değiştirir. Bağlantı yoksa {@code 404}; sürüm uyuşmazsa {@code 409}. İzin zaten
     * istenen değerdeyse hiçbir şey değişmez (idempotent).
     */
    @Transactional
    public LinkedAccountView updateVisibility(UUID userId, String rawProvider, boolean show, long expectedVersion) {
        LinkedProvider provider = LinkedProvider.parse(rawProvider).orElseThrow(LinkedAccountService::notFound);
        LinkedAccount account = accounts.findByUserIdAndProvider(userId, provider)
                .filter(LinkedAccount::isActive).orElseThrow(LinkedAccountService::notFound);
        if (account.getVersion() != expectedVersion) {
            throw new UserException(UserErrorCode.CONCURRENT_MODIFICATION);
        }
        if (account.changeShowOnProfile(show, clock.instant())) {
            accounts.flush();
        }
        return LinkedAccountView.of(account);
    }

    private LinkedAccountView view(UUID userId, LinkedProvider provider) {
        return accounts.findByUserIdAndProvider(userId, provider).map(LinkedAccountView::of)
                .orElseGet(() -> LinkedAccountView.unlinked(provider));
    }

    private static UserException notFound() {
        return new UserException(UserErrorCode.LINKED_ACCOUNT_NOT_FOUND);
    }
}
