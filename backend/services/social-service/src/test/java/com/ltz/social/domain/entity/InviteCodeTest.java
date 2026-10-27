package com.ltz.social.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ltz.social.domain.exception.SocialErrorCode;
import com.ltz.social.domain.exception.SocialException;

class InviteCodeTest {

    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");

    private InviteCode code(int maxUses) {
        return InviteCode.issue(UUID.randomUUID(), "0123456789", NOW, Duration.ofDays(30), maxUses);
    }

    @Test
    void yeniKodKullanilabilir_sureVeSinirAyarlanir() {
        InviteCode code = code(3);

        assertThat(code.isUsableAt(NOW)).isTrue();
        assertThat(code.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
        assertThat(code.getMaxUses()).isEqualTo(3);
        assertThat(code.getUsedCount()).isZero();
        assertThat(code.getRevokedAt()).isNull();
    }

    @Test
    void sureDolunca_iptalEdilincevekullanimBitincekullanilamaz() {
        assertThat(code(3).isUsableAt(NOW.plus(Duration.ofDays(30)))).isFalse();
        assertThat(code(3).isUsableAt(NOW.plus(Duration.ofDays(30)).minusSeconds(1))).isTrue();

        InviteCode revoked = code(3);
        revoked.revoke(NOW);
        assertThat(revoked.isUsableAt(NOW)).isFalse();

        InviteCode exhausted = code(1);
        exhausted.recordUse(NOW);
        assertThat(exhausted.isUsableAt(NOW)).isFalse();
    }

    @Test
    void kullanimSayaciArtar_kullanilamazKodtaINVITE_CODE_NOT_FOUND() {
        InviteCode code = code(2);
        code.recordUse(NOW);
        code.recordUse(NOW);

        assertThat(code.getUsedCount()).isEqualTo(2);
        assertThatThrownBy(() -> code.recordUse(NOW)).isInstanceOfSatisfying(SocialException.class,
                exception -> assertThat(exception.errorCode()).isEqualTo(SocialErrorCode.INVITE_CODE_NOT_FOUND));
        assertThat(code.getUsedCount()).isEqualTo(2);
    }

    @Test
    void iptalIdempotent_ilkZamanKorunur() {
        InviteCode code = code(1);
        code.revoke(NOW);
        code.revoke(NOW.plusSeconds(60));

        assertThat(code.getRevokedAt()).isEqualTo(NOW);
    }
}
