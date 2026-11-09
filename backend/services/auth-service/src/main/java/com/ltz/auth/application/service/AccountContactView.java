package com.ltz.auth.application.service;

import java.util.UUID;

import com.ltz.auth.domain.entity.Account;
import com.ltz.auth.domain.enums.AccountStatus;

/**
 * Mail Service'in gönderim anında adres çözmesi için dar görünüm: yalnız durum, adres ve doğrulanmışlık.
 * Parola, token, rol vb. içermez; adres loglanmaz ({@link #toString()} maskeler).
 */
public record AccountContactView(UUID accountId, AccountStatus status, String email, boolean emailVerified) {

    /** Adresi olmayan (yalnız Steam) ya da anonimleştirilmiş hesapta {@code email} null'dır. */
    public static AccountContactView of(Account account) {
        boolean usable = account.hasEmail() && !account.isIdentityAnonymized();
        return new AccountContactView(account.getId(), account.getStatus(), usable ? account.getEmail() : null,
                usable && account.getEmailVerifiedAt() != null);
    }

    @Override
    public String toString() {
        return "AccountContactView[accountId=" + accountId + ", status=" + status + ", email=***]";
    }
}
