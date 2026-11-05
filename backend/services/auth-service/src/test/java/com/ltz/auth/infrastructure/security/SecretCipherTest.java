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
    void sifrelemeGeriCozulurHerSeferindeFarkliNonceKullanilir() {
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

        UUID otherAccountId = UUID.randomUUID();
        EncryptedSecret tamperedSecret = new EncryptedSecret(tampered, encrypted.nonce(), encrypted.keyVersion());
        SecretCipher otherKeyCipher = new SecretCipher(properties(key(SecretCipher.KEY_BYTES)));

        assertThatThrownBy(() -> cipher.decrypt(encrypted, otherAccountId)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt(tamperedSecret, accountId)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> otherKeyCipher.decrypt(encrypted, accountId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void yanlisUzunluktakiAnahtarReddedilirMesajAnahtariIcermez() {
        String shortKey = key(16);
        TwoFactorProperties shortKeyProperties = properties(shortKey);
        TwoFactorProperties invalidKeyProperties = properties("not base64 $$$");

        assertThatThrownBy(() -> new SecretCipher(shortKeyProperties))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining(shortKey).hasNoCause();
        assertThatThrownBy(() -> new SecretCipher(invalidKeyProperties))
                .isInstanceOf(IllegalStateException.class).hasNoCause();
    }

    @Test
    void rotasyonEskiSurumuCozerYeniSifrelemeleriGuncelSurumleYapar() {
        String oldKey = key(SecretCipher.KEY_BYTES);
        String newKey = key(SecretCipher.KEY_BYTES);
        UUID accountId = UUID.randomUUID();
        SecretCipher before = new SecretCipher(properties(oldKey, 1, null));
        EncryptedSecret legacy = before.encrypt(SECRET, accountId);

        SecretCipher rotated = new SecretCipher(properties(newKey, 2, "1=" + oldKey));

        assertThat(legacy.keyVersion()).isEqualTo((short) 1);
        assertThat(rotated.currentKeyVersion()).isEqualTo((short) 2);
        assertThat(rotated.isCurrent(legacy.keyVersion())).isFalse();
        assertThat(rotated.decrypt(legacy, accountId)).isEqualTo(SECRET);
        EncryptedSecret fresh = rotated.encrypt(SECRET, accountId);
        assertThat(fresh.keyVersion()).isEqualTo((short) 2);
        assertThat(rotated.isCurrent(fresh.keyVersion())).isTrue();
        assertThat(rotated.decrypt(fresh, accountId)).isEqualTo(SECRET);
        // Eski şifreleyici yeni sürümü çözemez (anahtarı bilmez).
        assertThatThrownBy(() -> before.decrypt(fresh, accountId)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void birdenCokEskiSurumVeBosluklarKabulEdilirBilinmeyenSurumReddedilir() {
        String key1 = key(SecretCipher.KEY_BYTES);
        String key2 = key(SecretCipher.KEY_BYTES);
        UUID accountId = UUID.randomUUID();
        EncryptedSecret v1 = new SecretCipher(properties(key1, 1, null)).encrypt(SECRET, accountId);
        EncryptedSecret v2 = new SecretCipher(properties(key2, 2, null)).encrypt(SECRET, accountId);

        SecretCipher rotated = new SecretCipher(properties(key(SecretCipher.KEY_BYTES), 3, " 1=" + key1 + " , 2=" + key2 + ","));

        assertThat(rotated.decrypt(v1, accountId)).isEqualTo(SECRET);
        assertThat(rotated.decrypt(v2, accountId)).isEqualTo(SECRET);
        EncryptedSecret unknown = new EncryptedSecret(v1.ciphertext(), v1.nonce(), (short) 9);
        assertThatThrownBy(() -> rotated.decrypt(unknown, accountId)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported secret key version");
    }

    @Test
    void gecersizOncekiAnahtarListesiAcilistaReddedilirMesajAnahtariIcermez() {
        String current = key(SecretCipher.KEY_BYTES);
        String other = key(SecretCipher.KEY_BYTES);
        String shortKey = key(16);

        for (String bad : new String[] {"1=" + shortKey, "x=" + other, "0=" + other, "2=" + other, "1" + other,
                "1=" + other + ",1=" + other, "=" + other, "1=not base64 $$$"}) {
            assertThatThrownBy(() -> new SecretCipher(properties(current, 2, bad)))
                    .as("reddedilmeli: " + bad.length()).isInstanceOf(IllegalStateException.class)
                    .hasMessageNotContaining(other).hasMessageNotContaining(shortKey).hasNoCause();
        }
        // Güncel sürümle çakışan önceki sürüm ("2=") belirsizdir; boş liste ise rotasyon yok demektir.
        assertThat(new SecretCipher(properties(current, 2, "  ")).currentKeyVersion()).isEqualTo((short) 2);
    }

    private static String key(int bytes) {
        byte[] key = new byte[bytes];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    private static TwoFactorProperties properties(String key) {
        return properties(key, 1, null);
    }

    private static TwoFactorProperties properties(String key, int version, String previousKeys) {
        return new TwoFactorProperties("LTZ", key, Duration.ofMinutes(5), 5, 10, 1, Set.of(), false, version,
                previousKeys);
    }
}
