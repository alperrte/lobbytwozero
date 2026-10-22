package com.ltz.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ltz.auth.domain.enums.VerificationPurpose;
import com.ltz.auth.infrastructure.config.VerificationProperties;

class VerificationCodeHasherTest {

    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final VerificationPurpose PURPOSE = VerificationPurpose.EMAIL_VERIFICATION;

    @Test
    void ayniGirdiAyniOzetiUretir_ozetHexVeAcikKoduIcermez() {
        VerificationCodeHasher hasher = hasherWithKeyBytes(32);

        String hash = hasher.hash(ACCOUNT, PURPOSE, "012345");

        assertThat(hash).matches("^[0-9a-f]{64}$").doesNotContain("012345")
                .isEqualTo(hasher.hash(ACCOUNT, PURPOSE, "012345"));
    }

    @Test
    void ozetKodaHesabaVeAnahtaraBaglidir() {
        VerificationCodeHasher hasher = hasherWithKeyBytes(32);
        String hash = hasher.hash(ACCOUNT, PURPOSE, "012345");

        assertThat(hasher.hash(ACCOUNT, PURPOSE, "012346")).isNotEqualTo(hash);
        assertThat(hasher.hash(UUID.randomUUID(), PURPOSE, "012345")).isNotEqualTo(hash);
        assertThat(hasherWithKeyBytes(32).hash(ACCOUNT, PURPOSE, "012345")).isNotEqualTo(hash);
    }

    @Test
    void kisaAnahtarReddedilir_mesajAnahtariIcermez() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new VerificationCodeHasher(properties(shortKey)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(shortKey)
                .hasNoCause();
    }

    @Test
    void base64OlmayanAnahtarReddedilir_mesajAnahtariIcermez() {
        String invalidKey = "not base64 $$$ value";

        assertThatThrownBy(() -> new VerificationCodeHasher(properties(invalidKey)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(invalidKey)
                .hasNoCause();
    }

    @Test
    void ayarNesnesininMetinGosterimiAnahtariMaskeler() {
        String key = randomKey(32);

        assertThat(properties(key).toString()).doesNotContain(key).contains("hmacKey=***");
    }

    private static VerificationCodeHasher hasherWithKeyBytes(int bytes) {
        return new VerificationCodeHasher(properties(randomKey(bytes)));
    }

    private static String randomKey(int bytes) {
        byte[] key = new byte[bytes];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    private static VerificationProperties properties(String key) {
        return new VerificationProperties(Duration.ofMinutes(10), 5, Duration.ofSeconds(60), 5, key);
    }
}
