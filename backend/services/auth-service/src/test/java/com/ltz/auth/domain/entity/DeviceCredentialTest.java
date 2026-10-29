package com.ltz.auth.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ltz.auth.domain.enums.DeviceRevokeReason;
import com.ltz.auth.domain.enums.DeviceStatus;

class DeviceCredentialTest {

    private static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");
    private static final String HASH = "a".repeat(64);

    private final DeviceCredential device = DeviceCredential.bind(UUID.randomUUID(), "PC", HASH, NOW, Duration.ofDays(365));

    @Test
    void yeniCihazAktifVeSuresiDolunceKullanilamaz() {
        assertThat(device.getStatus()).isEqualTo(DeviceStatus.ACTIVE);
        assertThat(device.isUsableAt(NOW.plus(Duration.ofDays(364)))).isTrue();
        assertThat(device.isUsableAt(NOW.plus(Duration.ofDays(365)))).isFalse();
    }

    @Test
    void ozetKarsilastirmasiYalnizTamEsleseniKabulEderNullAdayiReddeder() {
        assertThat(device.hasHash(HASH)).isTrue();
        assertThat(device.hasHash("b".repeat(64))).isFalse();
        assertThat(device.hasHash("a".repeat(63))).isFalse();
        assertThat(device.hasHash(null)).isFalse();
    }

    @Test
    void iptalIdempotentIlkNedenKorunurIptalliCihazKullanilamaz() {
        assertThat(device.revoke(DeviceRevokeReason.USER, NOW)).isTrue();
        assertThat(device.revoke(DeviceRevokeReason.PASSWORD_CHANGED, NOW.plusSeconds(5))).isFalse();

        assertThat(device.getRevokeReason()).isEqualTo(DeviceRevokeReason.USER);
        assertThat(device.getRevokedAt()).isEqualTo(NOW);
        assertThat(device.isUsableAt(NOW)).isFalse();
    }

    @Test
    void sonKullanimYalnizEsikGectigindeYazilir() {
        Duration resolution = Duration.ofMinutes(5);

        device.markUsed(NOW, resolution);
        device.markUsed(NOW.plus(Duration.ofMinutes(4)), resolution);
        assertThat(device.getLastUsedAt()).isEqualTo(NOW);

        device.markUsed(NOW.plus(Duration.ofMinutes(6)), resolution);
        assertThat(device.getLastUsedAt()).isEqualTo(NOW.plus(Duration.ofMinutes(6)));
    }
}
