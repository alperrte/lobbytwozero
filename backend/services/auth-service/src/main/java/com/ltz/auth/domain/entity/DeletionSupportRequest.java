package com.ltz.auth.domain.entity;

import java.time.Instant;
import java.util.UUID;

import com.ltz.auth.domain.enums.DeletionSupportStatus;

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

/** B/C türü hesabın yöneticiye açtığı silme talebi. Onay standart silme sürecini başlatır; ret hesabı değiştirmez. */
@Entity
@Table(name = "account_deletion_support_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeletionSupportRequest {

    public static final int TEXT_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DeletionSupportStatus status;

    @Column(length = TEXT_MAX_LENGTH, updatable = false)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decision_note", length = TEXT_MAX_LENGTH)
    private String decisionNote;

    public static DeletionSupportRequest open(UUID accountId, String message, Instant now) {
        DeletionSupportRequest request = new DeletionSupportRequest();
        request.accountId = accountId;
        request.status = DeletionSupportStatus.PENDING;
        request.message = message;
        request.createdAt = now;
        return request;
    }

    public boolean isPending() {
        return status == DeletionSupportStatus.PENDING;
    }

    public void approve(UUID adminId, String note, Instant now) {
        decide(DeletionSupportStatus.APPROVED, adminId, note, now);
    }

    public void reject(UUID adminId, String note, Instant now) {
        decide(DeletionSupportStatus.REJECTED, adminId, note, now);
    }

    private void decide(DeletionSupportStatus target, UUID adminId, String note, Instant now) {
        if (!isPending()) {
            throw new IllegalStateException("Only a pending request can be decided");
        }
        status = target;
        decidedBy = adminId;
        decisionNote = note;
        decidedAt = now;
    }
}
