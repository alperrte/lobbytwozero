package com.ltz.auth.infrastructure.security;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.ltz.auth.infrastructure.config.TwoFactorProperties;

/**
 * TOTP secret'larını AES-256-GCM ile şifreler. Hesap kimliği ek doğrulanmış veri (AAD) olarak bağlanır;
 * şifreli değer başka bir hesabın satırına kopyalanırsa çözülemez.
 * <p>
 * <b>Anahtar rotasyonu (S9):</b> her satırın {@code key_version} değeri hangi anahtarla şifrelendiğini söyler. Güncel
 * anahtar ({@code AUTH_TOTP_ENCRYPTION_KEY}) {@code AUTH_TOTP_ENCRYPTION_KEY_VERSION} sürümündedir ve yeni şifrelemeler
 * onunla yapılır; eski sürümler {@code AUTH_TOTP_ENCRYPTION_PREVIOUS_KEYS} ({@code 1=<base64>,2=<base64>}) ile yalnız
 * çözülür. Eski sürümdeki satır, TOTP ile her başarılı doğrulamada güncel anahtarla yeniden şifrelenir. Hiçbiri
 * verilmezse davranış önceki gibidir (tek anahtar, sürüm 1).
 */
@Component
public class SecretCipher {

    static final int KEY_BYTES = 32;
    static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int UUID_BYTES = 16;

    private final Map<Short, SecretKeySpec> keys = new LinkedHashMap<>();
    private final short currentVersion;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(TwoFactorProperties properties) {
        this.currentVersion = (short) properties.encryptionKeyVersion();
        keys.put(currentVersion, new SecretKeySpec(decodeKey(properties.encryptionKey(), invalidCurrentKeyMessage()),
                ALGORITHM));
        parsePreviousKeys(properties.previousEncryptionKeys());
    }

    /** Yeni şifrelemelerde kullanılan anahtarın sürümü. */
    public short currentKeyVersion() {
        return currentVersion;
    }

    /** Satır güncel anahtarla şifreli mi; değilse sonraki başarılı doğrulamada yeniden şifrelenmelidir. */
    public boolean isCurrent(short keyVersion) {
        return keyVersion == currentVersion;
    }

    public EncryptedSecret encrypt(byte[] plaintext, UUID accountId) {
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keys.get(currentVersion), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad(accountId));
            return new EncryptedSecret(cipher.doFinal(plaintext), nonce, currentVersion);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Secret encryption failed", exception);
        }
    }

    public byte[] decrypt(EncryptedSecret secret, UUID accountId) {
        SecretKeySpec key = keys.get(secret.keyVersion());
        if (key == null) {
            throw new IllegalStateException("Unsupported secret key version");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, secret.nonce()));
            cipher.updateAAD(aad(accountId));
            return cipher.doFinal(secret.ciphertext());
        } catch (GeneralSecurityException _) {
            throw new IllegalStateException("Secret decryption failed");
        }
    }

    private void parsePreviousKeys(String previousKeys) {
        if (previousKeys == null || previousKeys.isBlank()) {
            return;
        }
        for (String entry : previousKeys.split(",")) {
            String trimmed = entry.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            int separator = trimmed.indexOf('=');
            if (separator <= 0) {
                throw new IllegalStateException(invalidPreviousKeysMessage());
            }
            short version = parseVersion(trimmed.substring(0, separator).strip());
            // Aynı sürüm iki kez ya da güncel sürümle çakışıyorsa hangi anahtarın doğru olduğu belirsizdir.
            if (keys.containsKey(version)) {
                throw new IllegalStateException(invalidPreviousKeysMessage());
            }
            keys.put(version, new SecretKeySpec(decodeKey(trimmed.substring(separator + 1), invalidPreviousKeysMessage()),
                    ALGORITHM));
        }
    }

    private static short parseVersion(String text) {
        try {
            int version = Integer.parseInt(text);
            if (version < 1 || version > Short.MAX_VALUE) {
                throw new IllegalStateException(invalidPreviousKeysMessage());
            }
            return (short) version;
        } catch (NumberFormatException _) {
            throw new IllegalStateException(invalidPreviousKeysMessage());
        }
    }

    private static byte[] aad(UUID accountId) {
        return ByteBuffer.allocate(UUID_BYTES)
                .putLong(accountId.getMostSignificantBits())
                .putLong(accountId.getLeastSignificantBits())
                .array();
    }

    // Hata mesajları ve cause zinciri anahtarın herhangi bir parçasını içermez.
    private static byte[] decodeKey(String encodedKey, String errorMessage) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encodedKey.strip());
        } catch (IllegalArgumentException _) {
            throw new IllegalStateException(errorMessage);
        }
        if (decoded.length != KEY_BYTES) {
            throw new IllegalStateException(errorMessage);
        }
        return decoded;
    }

    private static String invalidCurrentKeyMessage() {
        return "ltz.auth.two-factor.encryption-key (AUTH_TOTP_ENCRYPTION_KEY) must be a Base64 value of exactly "
                + KEY_BYTES + " bytes";
    }

    private static String invalidPreviousKeysMessage() {
        return "ltz.auth.two-factor.previous-encryption-keys (AUTH_TOTP_ENCRYPTION_PREVIOUS_KEYS) must be "
                + "'version=Base64,...' with distinct versions different from the current one and " + KEY_BYTES
                + "-byte keys";
    }

    public record EncryptedSecret(byte[] ciphertext, byte[] nonce, short keyVersion) {

        @Override
        public boolean equals(Object other) {
            return other instanceof EncryptedSecret(byte[] otherCiphertext, byte[] otherNonce, short otherVersion)
                    && keyVersion == otherVersion && Arrays.equals(ciphertext, otherCiphertext)
                    && Arrays.equals(nonce, otherNonce);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * Short.hashCode(keyVersion) + Arrays.hashCode(ciphertext)) + Arrays.hashCode(nonce);
        }

        @Override
        public String toString() {
            return "EncryptedSecret[ciphertext=***, nonce=***, keyVersion=" + keyVersion + "]";
        }
    }
}
