package com.ltz.auth.domain.enums;

public enum DeletionRequestStatus {
    COORDINATING,
    COMPLETED,
    /** Bekleme süresi içinde kullanıcı vazgeçti; görevler silindi, hesap yeniden ACTIVE. */
    CANCELLED
}
