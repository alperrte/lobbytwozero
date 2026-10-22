package com.ltz.auth.domain.entity;

import java.time.Instant;
import java.util.UUID;

import com.ltz.auth.domain.enums.ExternalProvider;

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

/** Sağlayıcının değişmez kimliğiyle (Steam ID / Google sub) hesap arasındaki bağ. */
@Entity
@Table(name = "external_identities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExternalIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private ExternalProvider provider;

    @Column(name = "provider_subject", nullable = false, updatable = false, length = 64)
    private String providerSubject;

    @Column(name = "linked_at", nullable = false, updatable = false)
    private Instant linkedAt;

    public static ExternalIdentity link(UUID accountId, ExternalProvider provider, String subject, Instant now) {
        ExternalIdentity identity = new ExternalIdentity();
        identity.accountId = accountId;
        identity.provider = provider;
        identity.providerSubject = subject;
        identity.linkedAt = now;
        return identity;
    }
}
