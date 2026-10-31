package com.ltz.auth.domain.entity;

import java.time.Instant;
import java.util.UUID;

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

/**
 * Oturumlu kullanıcının başlattığı harici kimlik bağlama akışı. Hangi hesaba bağlanacağı akışa yazılır; state yalnız özet
 * olarak saklanır ve tek kullanımlıktır. Giriş akışlarından ({@link OAuthFlow}) ayrıdır.
 */
@Entity
@Table(name = "oauth_link_flows")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OAuthLinkFlow {

    @Id
    @Column(name = "state_hash", length = 64)
    private String stateHash;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private ExternalProvider provider;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    public static OAuthLinkFlow start(String stateHash, UUID accountId, ExternalProvider provider, Instant now,
                                      Instant expiresAt) {
        OAuthLinkFlow flow = new OAuthLinkFlow();
        flow.stateHash = stateHash;
        flow.accountId = accountId;
        flow.provider = provider;
        flow.createdAt = now;
        flow.expiresAt = expiresAt;
        return flow;
    }
}
