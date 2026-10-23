package com.ltz.hardware.domain.entity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;

public final class HardwareReviewText {
    private HardwareReviewText() { }

    public static String clean(String value, int limit) {
        validate(value, limit);
        String result = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replace("\r\n", "\n").replace('\r', '\n').strip();
        validate(result, limit);
        return result;
    }

    private static void validate(String value, int limit) {
        if (value == null || value.isBlank() || value.length() > limit || value.codePoints().anyMatch(c ->
                c == '<' || c == '>' || (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t'))) {
            throw new IllegalArgumentException("Geçersiz yorum metni");
        }
    }

    public static String fingerprint(String body) {
        return hash(clean(body, 5000).toLowerCase(Locale.ROOT).replaceAll("(?U)\\s+", " "));
    }

    public static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Gerekli hash algoritması kullanılamıyor", exception);
        }
    }
}
