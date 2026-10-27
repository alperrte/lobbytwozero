package com.ltz.auth.domain.entity;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.ltz.auth.domain.enums.AuthMethod;
import com.ltz.auth.domain.enums.SessionRevokeReason;

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

/** Bir cihazdaki oturum; refresh token ailesinin sahibidir. Kimliği access token'da "sid" olarak taşınır. */
@Entity
@Table(name = "auth_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthSession {

    private static final String METHOD_SEPARATOR = ",";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "token_version", nullable = false, updatable = false)
    private long tokenVersion;

    @Getter(AccessLevel.NONE)
    @Column(name = "auth_methods", nullable = false, updatable = false, length = 32)
    private String authMethods;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoke_reason", length = 32)
    private SessionRevokeReason revokeReason;

    public static AuthSession open(UUID accountId, long tokenVersion, List<AuthMethod> methods,
                                   Instant now, Instant expiresAt) {
        AuthSession session = new AuthSession();
        session.accountId = accountId;
        session.tokenVersion = tokenVersion;
        session.authMethods = methods.stream().map(Enum::name).collect(Collectors.joining(METHOD_SEPARATOR));
        session.createdAt = now;
        session.expiresAt = expiresAt;
        return session;
    }

    public List<AuthMethod> authMethods() {
        return Arrays.stream(authMethods.split(METHOD_SEPARATOR)).map(AuthMethod::valueOf).toList();
    }

    public boolean isActiveAt(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    public void revoke(SessionRevokeReason reason, Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
            revokeReason = reason;
        }
    }
}
