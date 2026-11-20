package com.ltz.auth.domain.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Hesabın daha önce giriş yaptığı bir tarayıcı. Yalnız rastgele cihaz çerezinin SHA-256 özeti tutulur; IP, User-Agent
 * ve konum saklanmaz. Kayıtlar yalnız yerel SQL ile yazılır (eşzamanlı girişte çakışma güvenli), bu sınıf şemayı
 * doğrulamak ve okumak içindir.
 */
@Entity
@Table(name = "known_devices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KnownDevice {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Getter(AccessLevel.NONE)
    @Column(name = "device_hash", nullable = false, updatable = false, length = 64)
    private String deviceHash;

    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "notified_at")
    private Instant notifiedAt;
}
