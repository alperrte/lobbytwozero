package com.ltz.auth.infrastructure.security;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.OptionalLong;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

/**
 * RFC 6238 TOTP (HMAC-SHA1, 30 sn, 6 hane) — Authenticator uygulamalarının ortak varsayılanı.
 * Bu parametreler protokol sabitidir; değiştirmek kayıtlı tüm cihazları bozar.
 */
@Component
public class TotpCodes {

    public static final int DIGITS = 6;
    static final Duration TIME_STEP = Duration.ofSeconds(30);
    static final int SECRET_BYTES = 20;
    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final String OTPAUTH_ALGORITHM = "SHA1";
    private static final int MODULUS = (int) Math.pow(10, DIGITS);
    private static final String CODE_FORMAT = "%0" + DIGITS + "d";
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int BASE32_BITS = 5;
    private static final int BYTE_BITS = 8;
    private static final int BYTE_MASK = 0xFF;
    private static final int OFFSET_MASK = 0x0F;
    private static final int SIGN_MASK = 0x7FFFFFFF;

    private final SecureRandom random = new SecureRandom();

    public byte[] newSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        random.nextBytes(secret);
        return secret;
    }

    public long stepAt(Instant instant) {
        return instant.getEpochSecond() / TIME_STEP.toSeconds();
    }

    public String codeAt(byte[] secret, long step) {
        try {
            // RFC 6238 varsayılanı HMAC-SHA1; authenticator uygulamalarıyla uyum için zorunlu (HMAC-SHA1 kırık değildir).
            Mac mac = Mac.getInstance(HMAC_ALGORITHM); // NOSONAR java:S4790
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
            int offset = hash[hash.length - 1] & OFFSET_MASK;
            int binary = (ByteBuffer.wrap(hash, offset, Integer.BYTES).getInt() & SIGN_MASK) % MODULUS;
            return String.format(Locale.ROOT, CODE_FORMAT, binary);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("TOTP computation failed", exception);
        }
    }

    /** Saat kaymasına izin verilen pencerede eşleşen zaman adımını döndürür; karşılaştırma sabit zamanlıdır. */
    public OptionalLong matchingStep(byte[] secret, String code, Instant now, int allowedSkewSteps) {
        long current = stepAt(now);
        byte[] candidate = code.getBytes(StandardCharsets.US_ASCII);
        OptionalLong match = OptionalLong.empty();
        for (long step = current - allowedSkewSteps; step <= current + allowedSkewSteps; step++) {
            if (MessageDigest.isEqual(codeAt(secret, step).getBytes(StandardCharsets.US_ASCII), candidate)) {
                match = OptionalLong.of(step);
            }
        }
        return match;
    }

    public String otpauthUri(String issuer, String accountName, byte[] secret) {
        String label = encode(issuer + ":" + accountName);
        return "otpauth://totp/" + label + "?secret=" + base32(secret) + "&issuer=" + encode(issuer)
                + "&algorithm=" + OTPAUTH_ALGORITHM + "&digits=" + DIGITS + "&period=" + TIME_STEP.toSeconds();
    }

    public String base32(byte[] data) {
        StringBuilder encoded = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte value : data) {
            buffer = (buffer << BYTE_BITS) | (value & BYTE_MASK);
            bitsLeft += BYTE_BITS;
            while (bitsLeft >= BASE32_BITS) {
                encoded.append(BASE32_ALPHABET.charAt((buffer >> (bitsLeft - BASE32_BITS)) & (BASE32_ALPHABET.length() - 1)));
                bitsLeft -= BASE32_BITS;
            }
        }
        if (bitsLeft > 0) {
            encoded.append(BASE32_ALPHABET.charAt((buffer << (BASE32_BITS - bitsLeft)) & (BASE32_ALPHABET.length() - 1)));
        }
        return encoded.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
