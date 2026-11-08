package com.ltz.auth.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.ltz.auth.domain.enums.VerificationPurpose;
import com.ltz.auth.infrastructure.config.VerificationProperties;

/**
 * 6 haneli kodun düşük entropisi nedeniyle düz SHA-256 yerine gizli anahtarlı HMAC-SHA256 kullanılır;
 * DB sızsa bile anahtar olmadan kodlar denenemez. Hesap ve amaç girdiye bağlanır, özet başka kayıtta geçmez.
 * <p>
 * <b>Anahtar rotasyonu (S9):</b> yeni özetler her zaman güncel anahtarla üretilir. {@code AUTH_OTP_HMAC_PREVIOUS_KEY}
 * verilirse, kayıtlı özetle <i>karşılaştırma</i> yapan yerler ({@link #allHashes}) önceki anahtarın özetini de dener;
 * böylece anahtar değişince henüz kullanılmamış kurtarma kodları ve bekleyen e-posta kodları geçersiz kalmaz. Kısa
 * ömürlü değerler (deneme sayaçları, PKCE/nonce) rotasyonda kendiliğinden yenilenir.
 */
@Component
public class VerificationCodeHasher {

    static final String ALGORITHM = "HmacSHA256";
    static final int MIN_KEY_BYTES = 32;
    private static final char FIELD_SEPARATOR = ':';

    private final SecretKeySpec key;
    private final SecretKeySpec previousKey;

    public VerificationCodeHasher(VerificationProperties properties) {
        this.key = new SecretKeySpec(decodeKey(properties.hmacKey(), invalidKeyMessage()), ALGORITHM);
        String previous = properties.previousHmacKey();
        this.previousKey = previous == null || previous.isBlank()
                ? null
                : new SecretKeySpec(decodeKey(previous, invalidPreviousKeyMessage()), ALGORITHM);
    }

    public String hash(UUID accountId, VerificationPurpose purpose, String code) {
        return hmacHex(key, purpose.name() + FIELD_SEPARATOR + accountId + FIELD_SEPARATOR + code);
    }

    /**
     * Düşük entropili diğer değerler (giriş kimliği, kurtarma kodu) için aynı anahtarla, alan ayrımlı HMAC.
     * domain öneki farklı kullanım amaçlarının özetlerinin çakışmasını engeller.
     */
    public String hash(String domain, String value) {
        return hmacHex(key, domain + FIELD_SEPARATOR + value);
    }

    /** Karşılaştırma için kabul edilen özetler: önce güncel, varsa sonra önceki anahtarınki. */
    public List<String> allHashes(UUID accountId, VerificationPurpose purpose, String code) {
        String input = purpose.name() + FIELD_SEPARATOR + accountId + FIELD_SEPARATOR + code;
        return allHashes(input);
    }

    /** {@link #hash(String, String)} için karşılaştırma özetleri (güncel, varsa önceki anahtar). */
    public List<String> allHashes(String domain, String value) {
        return allHashes(domain + FIELD_SEPARATOR + value);
    }

    private List<String> allHashes(String input) {
        List<String> hashes = new ArrayList<>(2);
        hashes.add(hmacHex(key, input));
        if (previousKey != null) {
            hashes.add(hmacHex(previousKey, input));
        }
        return List.copyOf(hashes);
    }

    private static String hmacHex(SecretKeySpec secretKey, String input) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(secretKey);
            return HexFormat.of().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC computation failed", exception);
        }
    }

    // Hata mesajları ve cause zinciri anahtarın herhangi bir parçasını içermez.
    private static byte[] decodeKey(String encodedKey, String errorMessage) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException _) {
            throw new IllegalStateException(errorMessage);
        }
        if (decoded.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(errorMessage);
        }
        return decoded;
    }

    private static String invalidKeyMessage() {
        return "ltz.auth.verification.hmac-key (AUTH_OTP_HMAC_KEY) must be Base64 encoded and at least "
                + MIN_KEY_BYTES + " bytes";
    }

    private static String invalidPreviousKeyMessage() {
        return "ltz.auth.verification.previous-hmac-key (AUTH_OTP_HMAC_PREVIOUS_KEY) must be Base64 encoded and at least "
                + MIN_KEY_BYTES + " bytes";
    }
}
