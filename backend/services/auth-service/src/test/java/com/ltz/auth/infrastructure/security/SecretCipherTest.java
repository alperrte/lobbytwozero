package com.ltz.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ltz.auth.infrastructure.config.TwoFactorProperties;
import com.ltz.auth.infrastructure.security.SecretCipher.EncryptedSecret;

class SecretCipherTest {

    private static final byte[] SECRET = "totp-secret-bytes!!!".getBytes();

    private final SecretCipher cipher = new SecretCipher(properties(key(SecretCipher.KEY_BYTES)));

    @Test
    void sifrelemeGeriCozulur_herSeferindeFarkliNonceKullanilir() {
        UUID accountId = UUID.randomUUID();
        EncryptedSecret first = cipher.encrypt(SECRET, accountId);
        EncryptedSecret second = cipher.encrypt(SECRET, accountId);

        assertThat(cipher.decrypt(first, accountId)).isEqualTo(SECRET);
        assertThat(first.nonce()).hasSize(SecretCipher.NONCE_BYTES).isNotEqualTo(second.nonce());
        assertThat(first.ciphertext()).isNotEqualTo(SECRET);
    }

    @Test
    void baskaHesabaKopyalananVeyaBozulanSifreliDegerCozulmez() {
        UUID accountId = UUID.randomUUID();
        EncryptedSecret encrypted = cipher.encrypt(SECRET, accountId);
        byte[] tampered = encrypted.ciphertext().clone();
        tampered[0] ^= 1;

        assertThatThrownBy(() -> cipher.decrypt(encrypted, UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt(new EncryptedSecret(tampered, encrypted.nonce(), encrypted.keyVersion()),
                accountId)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SecretCipher(properties(key(SecretCipher.KEY_BYTES))).decrypt(encrypted, accountId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void yanlisUzunluktakiAnahtarReddedilir_mesajAnahtariIcermez() {
        String shortKey = key(16);

        assertThatThrownBy(() -> new SecretCipher(properties(shortKey)))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining(shortKey).hasNoCause();
        assertThatThrownBy(() -> new SecretCipher(properties("not base64 $$$")))
                .isInstanceOf(IllegalStateException.class).hasNoCause();
    }

    private static String key(int bytes) {
        byte[] key = new byte[bytes];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    private static TwoFactorProperties properties(String key) {
        return new TwoFactorProperties("LTZ", key, Duration.ofMinutes(5), 5, 10, 1, Set.of());
    }
}
