package com.ltz.auth.domain.enums;

/**
 * Kanonik hesap durumları (docs/database.md §3.1, architecture.md durum diyagramı).
 * Yalnız ACTIVE hesap korumalı işlem yapabilir; geçişler sonraki fazlarda eklenir.
 */
public enum AccountStatus {
    PENDING_VERIFICATION,
    ACTIVE,
    SUSPENDED,
    BANNED,
    DISABLED,
    DELETION_REQUESTED,
    DELETED
}
