package com.ltz.auth.domain.entity;

import java.time.Instant;

import com.ltz.auth.domain.enums.ExternalProvider;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Başlatılmış, henüz tamamlanmamış harici giriş akışı. state yalnız özet olarak saklanır; tek kullanımlıktır. */
@Entity
@Table(name = "oauth_flows")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OAuthFlow {

    @Id
    @Column(name = "state_hash", length = 64)
    private String stateHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private ExternalProvider provider;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    public static OAuthFlow start(String stateHash, ExternalProvider provider, Instant now, Instant expiresAt) {
        OAuthFlow flow = new OAuthFlow();
        flow.stateHash = stateHash;
        flow.provider = provider;
        flow.createdAt = now;
        flow.expiresAt = expiresAt;
        return flow;
    }
}
