package com.ltz.hardware.domain.entity;

import com.ltz.hardware.domain.exception.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.Getter;

@Entity
@Table(name = "hardware_sync_consents")
@Getter
public class HardwareSyncConsent {
    private static final String GRANTED = "GRANTED";
    @Id private UUID id;
    @Column(name = "profile_id", nullable = false, updatable = false) private UUID profileId;
    @Column(name = "device_id", nullable = false) private UUID deviceId;
    @Column(nullable = false) private UUID generation;
    @Column(nullable = false, length = 16) private String status;
    @Column(name = "granted_at", nullable = false) private Instant grantedAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "last_sequence", nullable = false) private long lastSequence;
    @Column(name = "last_captured_at") private Instant lastCapturedAt;
    @Column(name = "last_synced_at") private Instant lastSyncedAt;
    @Version private Long version;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected HardwareSyncConsent() { }
    public HardwareSyncConsent(UUID profileId, UUID deviceId) {
        this.id = UUID.randomUUID();
        this.profileId = Objects.requireNonNull(profileId);
        createdAt = Instant.now();
        grant(deviceId);
    }

    public void grant(UUID deviceId) {
        if (GRANTED.equals(status)) throw new HardwareConflictException();
        this.deviceId = Objects.requireNonNull(deviceId);
        generation = UUID.randomUUID();
        status = GRANTED;
        grantedAt = Instant.now();
        revokedAt = null;
        lastSequence = 0;
        lastCapturedAt = null;
        lastSyncedAt = null;
        updatedAt = grantedAt;
    }

    public void revoke() {
        status = "REVOKED";
        revokedAt = Instant.now();
        updatedAt = revokedAt;
    }

    public void requireGrant(UUID device, UUID expectedGeneration) {
        if (!deviceId.equals(device)) throw new HardwareNotFoundException();
        if (!GRANTED.equals(status) || !generation.equals(expectedGeneration)) throw new HardwareConflictException();
    }

    public void advance(long sequence, Instant capturedAt, Instant appliedAt) {
        if (!GRANTED.equals(status) || sequence <= lastSequence ||
                (lastCapturedAt != null && capturedAt.isBefore(lastCapturedAt))) throw new HardwareConflictException();
        lastSequence = sequence;
        lastCapturedAt = capturedAt;
        lastSyncedAt = appliedAt;
        updatedAt = appliedAt;
    }
}
