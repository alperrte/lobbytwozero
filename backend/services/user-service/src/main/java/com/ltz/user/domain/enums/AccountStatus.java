package com.ltz.user.domain.enums;

import java.util.Optional;

/**
 * Auth'taki hesap durumunun kopyası (Auth {@code AccountStatus}). Doğruluk kaynağı Auth'tur; burada yalnız
 * {@code AccountStatusChanged} olaylarıyla eşitlenir ve profilin görünürlüğünü belirler.
 */
public enum AccountStatus {
    PENDING_VERIFICATION,
    ACTIVE,
    SUSPENDED,
    BANNED,
    DISABLED,
    DELETION_REQUESTED,
    DELETED;

    /** Olay yükündeki durum adı; tanınmayan ad {@code empty} döner. */
    public static Optional<AccountStatus> parse(String name) {
        for (AccountStatus status : values()) {
            if (status.name().equals(name)) {
                return Optional.of(status);
            }
        }
        return Optional.empty();
    }

    /** Hesabı kısıtlayan durumlar: profil henüz yokken bile kaydedilmelidir (fail-closed). */
    public boolean isRestrictive() {
        return this == SUSPENDED || this == BANNED || this == DISABLED;
    }
}
