package com.ltz.auth.domain.entity;

import java.time.Instant;
import java.util.UUID;

import com.ltz.auth.domain.enums.RoleAction;
import com.ltz.auth.domain.enums.RoleCode;

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

/** Rol verme/alma işleminin denetim kaydı (kim, neden, hangi rol). */
@Entity
@Table(name = "account_role_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountRoleHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_code", nullable = false, updatable = false, length = 32)
    private RoleCode roleCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private RoleAction action;

    @Column(nullable = false, updatable = false, length = 500)
    private String reason;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static AccountRoleHistory of(UUID accountId, RoleCode role, RoleAction action, String reason, UUID actorId,
                                        Instant now) {
        AccountRoleHistory entry = new AccountRoleHistory();
        entry.accountId = accountId;
        entry.roleCode = role;
        entry.action = action;
        entry.reason = reason;
        entry.actorId = actorId;
        entry.createdAt = now;
        return entry;
    }
}
