package com.ltz.mail.infrastructure.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.ltz.mail.application.port.PayloadCipherPort;
import com.ltz.mail.infrastructure.config.CryptoProperties;

/**
 * Kuyruktaki doğrulama kodu/bağlantısının AES-256-GCM şifrelemesi. Her şifreleme 96 bit rastgele IV kullanır; kayıt
 * kimliği ve anahtar sürümü AAD'dir, bu yüzden başka kayda taşınan ya da kurcalanan veri çözülemez. İstek parmak izi
 * için ana anahtardan alan ayrıştırmalı ayrı bir HMAC anahtarı türetilir. Anahtar yoksa ya da 32 bayt değilse servis
 * açılmaz (fail-fast); anahtar hiçbir hata mesajına yazılmaz.
 */
@Component
public class AesGcmPayloadCipher implements PayloadCipherPort {

    static final short KEY_VERSION = 1;
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String HMAC = "HmacSHA256";
    private static final byte[] FINGERPRINT_DOMAIN = "ltz-mail-fingerprint-v1".getBytes(StandardCharsets.UTF_8);

    private final SecretKeySpec encryptionKey;
    private final SecretKeySpec fingerprintKey;
    private final SecureRandom random = new SecureRandom();

    public AesGcmPayloadCipher(CryptoProperties properties) {
        byte[] master = decodeKey(properties.payloadKey());
        this.encryptionKey = new SecretKeySpec(master, "AES");
        this.fingerprintKey = new SecretKeySpec(hmac(new SecretKeySpec(master, HMAC), FINGERPRINT_DOMAIN), HMAC);
        Arrays.fill(master, (byte) 0);
    }

    @Override
    public short currentKeyVersion() {
        return KEY_VERSION;
    }

    @Override
    public byte[] encrypt(UUID messageId, byte[] plain) {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(messageId, KEY_VERSION));
            byte[] encrypted = cipher.doFinal(plain);
            return ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
        } catch (GeneralSecurityException exception) {
            throw new PayloadCipherException("Payload could not be encrypted", exception);
        }
    }

    @Override
    public byte[] decrypt(UUID messageId, short keyVersion, byte[] encrypted) {
        if (keyVersion != KEY_VERSION || encrypted == null || encrypted.length <= IV_BYTES) {
            throw new PayloadCipherException("Payload cannot be decrypted");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey,
                    new GCMParameterSpec(TAG_BITS, Arrays.copyOfRange(encrypted, 0, IV_BYTES)));
            cipher.updateAAD(aad(messageId, keyVersion));
            return cipher.doFinal(encrypted, IV_BYTES, encrypted.length - IV_BYTES);
        } catch (GeneralSecurityException exception) {
            throw new PayloadCipherException("Payload cannot be decrypted", exception);
        }
    }

    @Override
    public String fingerprint(String canonicalRequest) {
        return HexFormat.of().formatHex(hmac(fingerprintKey, canonicalRequest.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] aad(UUID messageId, short keyVersion) {
        return ByteBuffer.allocate(Long.BYTES * 2 + Short.BYTES)
                .putLong(messageId.getMostSignificantBits())
                .putLong(messageId.getLeastSignificantBits())
                .putShort(keyVersion)
                .array();
    }

    private static byte[] hmac(SecretKeySpec key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(key);
            return mac.doFinal(data);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC is not available", exception);
        }
    }

    private static byte[] decodeKey(String encoded) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(encoded == null ? "" : encoded.strip());
        } catch (IllegalArgumentException _) {
            throw new IllegalStateException("ltz.mail.crypto.payload-key must be Base64 (MAIL_PAYLOAD_ENCRYPTION_KEY)");
        }
        if (key.length != KEY_BYTES) {
            throw new IllegalStateException(
                    "ltz.mail.crypto.payload-key must decode to exactly 32 bytes (MAIL_PAYLOAD_ENCRYPTION_KEY)");
        }
        return key;
    }
}
