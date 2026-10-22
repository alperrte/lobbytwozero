package com.ltz.auth.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * Yüksek entropili opaque değerler (refresh token, MFA challenge) ve kurtarma kodları üretir.
 * Opaque değerler 256 bit olduğundan özetleri için anahtarsız SHA-256 yeterlidir.
 */
@Component
public class SecureTokens {

    static final int OPAQUE_TOKEN_BYTES = 32;
    static final String RECOVERY_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    static final int RECOVERY_CODE_GROUP_LENGTH = 5;
    private static final char RECOVERY_CODE_SEPARATOR = '-';
    private static final String DIGEST = "SHA-256";

    private final SecureRandom random = new SecureRandom();

    public String newOpaqueToken() {
        byte[] bytes = new byte[OPAQUE_TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance(DIGEST).digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(DIGEST + " is not available", exception);
        }
    }

    /** "XXXXX-XXXXX" biçiminde, karışabilen karakterler (0/O, 1/I) çıkarılmış ~50 bit kurtarma kodu. */
    public String newRecoveryCode() {
        StringBuilder code = new StringBuilder();
        for (int index = 0; index < RECOVERY_CODE_GROUP_LENGTH * 2; index++) {
            if (index == RECOVERY_CODE_GROUP_LENGTH) {
                code.append(RECOVERY_CODE_SEPARATOR);
            }
            code.append(RECOVERY_CODE_ALPHABET.charAt(random.nextInt(RECOVERY_CODE_ALPHABET.length())));
        }
        return code.toString();
    }

    /** Kullanıcının yazdığı kurtarma kodunu karşılaştırma biçimine getirir (ayraç ve harf büyüklüğü önemsiz). */
    public String normalizeRecoveryCode(String code) {
        return code.strip().replace(String.valueOf(RECOVERY_CODE_SEPARATOR), "").toUpperCase(Locale.ROOT);
    }
}
