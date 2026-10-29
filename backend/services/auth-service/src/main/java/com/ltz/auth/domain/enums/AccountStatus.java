package com.ltz.auth.domain.enums;

/**
 * Kanonik hesap durumları (docs/database.md §3.1, architecture.md durum diyagramı).
 * Yalnız ACTIVE hesap korumalı işlem yapabilir.
 */
public enum AccountStatus {
    PENDING_VERIFICATION,
    ACTIVE,
    SUSPENDED,
    BANNED,
    DISABLED,
    DELETION_REQUESTED,
    DELETED;

    /**
     * Yönetici (AUTH-12) geçişleri. PENDING_VERIFICATION AUTH-02, DELETION_REQUESTED/DELETED AUTH-16 ile yönetilir.
     * Askı ve yasak yönetici kararıyla geri alınabilir (SUSPENDED/DISABLED/BANNED → ACTIVE); süreli otomatik açılma yoktur.
     */
    public boolean canTransitionTo(AccountStatus target) {
        return switch (this) {
            case ACTIVE -> target == SUSPENDED || target == BANNED || target == DISABLED;
            case SUSPENDED -> target == ACTIVE || target == BANNED;
            case DISABLED, BANNED -> target == ACTIVE;
            case PENDING_VERIFICATION, DELETION_REQUESTED, DELETED -> false;
        };
    }
}
