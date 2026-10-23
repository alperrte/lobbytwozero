package com.ltz.auth.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.ltz.auth.domain.enums.VerificationPurpose;
import com.ltz.auth.infrastructure.config.VerificationProperties;

/**
 * 6 haneli kodun düşük entropisi nedeniyle düz SHA-256 yerine gizli anahtarlı HMAC-SHA256 kullanılır;
 * DB sızsa bile anahtar olmadan kodlar denenemez. Hesap ve amaç girdiye bağlanır, özet başka kayıtta geçmez.
 */
@Component
public class VerificationCodeHasher {

    static final String ALGORITHM = "HmacSHA256";
    static final int MIN_KEY_BYTES = 32;
    private static final char FIELD_SEPARATOR = ':';

    private final SecretKeySpec key;

    public VerificationCodeHasher(VerificationProperties properties) {
        this.key = new SecretKeySpec(decodeKey(properties.hmacKey()), ALGORITHM);
    }

    public String hash(UUID accountId, VerificationPurpose purpose, String code) {
        return hmacHex(purpose.name() + FIELD_SEPARATOR + accountId + FIELD_SEPARATOR + code);
    }

    /**
     * Düşük entropili diğer değerler (giriş kimliği, kurtarma kodu) için aynı anahtarla, alan ayrımlı HMAC.
     * domain öneki farklı kullanım amaçlarının özetlerinin çakışmasını engeller.
     */
    public String hash(String domain, String value) {
        return hmacHex(domain + FIELD_SEPARATOR + value);
    }

    private String hmacHex(String input) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC computation failed", exception);
        }
    }

    // Hata mesajları ve cause zinciri anahtarın herhangi bir parçasını içermez.
    private static byte[] decodeKey(String encodedKey) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(invalidKeyMessage());
        }
        if (decoded.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(invalidKeyMessage());
        }
        return decoded;
    }

    private static String invalidKeyMessage() {
        return "ltz.auth.verification.hmac-key (AUTH_OTP_HMAC_KEY) must be Base64 encoded and at least "
                + MIN_KEY_BYTES + " bytes";
    }
}
