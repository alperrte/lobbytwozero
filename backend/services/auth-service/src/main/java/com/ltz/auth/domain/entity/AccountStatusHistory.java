package com.ltz.auth.domain.entity;

import java.time.Instant;
import java.util.UUID;

import com.ltz.auth.domain.enums.AccountStatus;

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

/** Yönetici kaynaklı hesap durumu değişikliğinin denetim kaydı (kim, neden, hangi durumdan hangisine). */
@Entity
@Table(name = "account_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, updatable = false, length = 32)
    private AccountStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false, length = 32)
    private AccountStatus toStatus;

    @Column(nullable = false, updatable = false, length = 500)
    private String reason;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static AccountStatusHistory of(UUID accountId, AccountStatus from, AccountStatus to, String reason,
                                          UUID actorId, Instant now) {
        AccountStatusHistory entry = new AccountStatusHistory();
        entry.accountId = accountId;
        entry.fromStatus = from;
        entry.toStatus = to;
        entry.reason = reason;
        entry.actorId = actorId;
        entry.createdAt = now;
        return entry;
    }
}
