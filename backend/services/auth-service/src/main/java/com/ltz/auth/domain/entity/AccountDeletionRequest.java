package com.ltz.auth.domain.entity;

import java.time.Instant;
import java.util.UUID;

import com.ltz.auth.domain.enums.DeletionRequestStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Hesap başına tek silme talebi (idempotent). Tüm servisler silmeyi onaylayınca COMPLETED olur. */
@Entity
@Table(name = "account_deletion_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountDeletionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DeletionRequestStatus status;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public static AccountDeletionRequest open(UUID accountId, Instant now) {
        AccountDeletionRequest request = new AccountDeletionRequest();
        request.accountId = accountId;
        request.status = DeletionRequestStatus.COORDINATING;
        request.requestedAt = now;
        return request;
    }

    public boolean isCompleted() {
        return status == DeletionRequestStatus.COMPLETED;
    }

    public void complete(Instant now) {
        status = DeletionRequestStatus.COMPLETED;
        completedAt = now;
    }
}
