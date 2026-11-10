package com.ltz.auth.application.service;

import java.util.List;

import com.ltz.auth.domain.entity.Account;
import com.ltz.auth.domain.enums.AccountTier;

/**
 * Hesabın güvence görünümü: parola var mı, bağlı sağlayıcılar, TOTP, kayıt yöntemi ve bunlardan hesaplanan tür.
 * Kişisel veri içermez (e-posta, kullanıcı adı yok).
 */
public record AccountProfile(boolean passwordSet, List<String> providers, boolean totpEnabled,
                             Account.RegistrationMethod registrationMethod, AccountTier tier) {

    public AccountProfile {
        providers = List.copyOf(providers);
    }
}
