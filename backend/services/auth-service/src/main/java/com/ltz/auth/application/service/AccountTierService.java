package com.ltz.auth.application.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ltz.auth.domain.entity.Account;
import com.ltz.auth.domain.entity.ExternalIdentity;
import com.ltz.auth.domain.enums.AccountTier;
import com.ltz.auth.domain.enums.TotpStatus;
import com.ltz.auth.infrastructure.persistence.repository.ExternalIdentityRepository;
import com.ltz.auth.infrastructure.persistence.repository.TotpCredentialRepository;

import lombok.RequiredArgsConstructor;

/**
 * Hesap türünü (A/B/C) hesaplar. Tür saklanmaz: 2FA kapatılınca ya da sağlayıcı bağlanınca bir sonraki çağrıda (access
 * token'da en geç bir sonraki yenilemede) güncel olur. {@link TwoFactorService}'e bağlı değildir; oturum servisi bu
 * sınıfı kullandığı için döngüsel bağımlılık oluşmaması gerekir.
 */
@Service
@RequiredArgsConstructor
public class AccountTierService {

    private final TotpCredentialRepository totpRepository;
    private final ExternalIdentityRepository identityRepository;

    public AccountProfile profileOf(Account account) {
        UUID accountId = account.getId();
        boolean totpEnabled = totpRepository.existsByAccountIdAndStatus(accountId, TotpStatus.ACTIVE);
        List<String> providers = identityRepository.findByAccountId(accountId).stream()
                .map(ExternalIdentity::getProvider).map(Enum::name).sorted().toList();
        AccountTier tier = AccountTier.of(account.getRegistrationMethod(), totpEnabled, !providers.isEmpty());
        return new AccountProfile(account.passwordHash().isPresent(), providers, totpEnabled,
                account.getRegistrationMethod(), tier);
    }

    public AccountTier tierOf(Account account) {
        return profileOf(account).tier();
    }
}
