package com.ltz.auth.infrastructure.security;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.ltz.auth.infrastructure.config.TwoFactorProperties;

/**
 * TOTP secret'larını AES-256-GCM ile şifreler. Hesap kimliği ek doğrulanmış veri (AAD) olarak bağlanır;
 * şifreli değer başka bir hesabın satırına kopyalanırsa çözülemez. keyVersion anahtar rotasyonuna hazırlıktır.
 */
@Component
public class SecretCipher {

    public static final short CURRENT_KEY_VERSION = 1;
    static final int KEY_BYTES = 32;
    static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int UUID_BYTES = 16;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(TwoFactorProperties properties) {
        this.key = new SecretKeySpec(decodeKey(properties.encryptionKey()), ALGORITHM);
    }

    public EncryptedSecret encrypt(byte[] plaintext, UUID accountId) {
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad(accountId));
            return new EncryptedSecret(cipher.doFinal(plaintext), nonce, CURRENT_KEY_VERSION);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Secret encryption failed", exception);
        }
    }

    public byte[] decrypt(EncryptedSecret secret, UUID accountId) {
        if (secret.keyVersion() != CURRENT_KEY_VERSION) {
            throw new IllegalStateException("Unsupported secret key version");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, secret.nonce()));
            cipher.updateAAD(aad(accountId));
            return cipher.doFinal(secret.ciphertext());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Secret decryption failed");
        }
    }

    private static byte[] aad(UUID accountId) {
        return ByteBuffer.allocate(UUID_BYTES)
                .putLong(accountId.getMostSignificantBits())
                .putLong(accountId.getLeastSignificantBits())
                .array();
    }

    private static byte[] decodeKey(String encodedKey) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encodedKey.strip());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(invalidKeyMessage());
        }
        if (decoded.length != KEY_BYTES) {
            throw new IllegalStateException(invalidKeyMessage());
        }
        return decoded;
    }

    private static String invalidKeyMessage() {
        return "ltz.auth.two-factor.encryption-key (AUTH_TOTP_ENCRYPTION_KEY) must be a Base64 value of exactly "
                + KEY_BYTES + " bytes";
    }

    public record EncryptedSecret(byte[] ciphertext, byte[] nonce, short keyVersion) {

        @Override
        public String toString() {
            return "EncryptedSecret[ciphertext=***, nonce=***, keyVersion=" + keyVersion + "]";
        }
    }
}
