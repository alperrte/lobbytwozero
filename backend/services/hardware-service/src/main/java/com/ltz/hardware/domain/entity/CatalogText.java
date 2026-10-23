package com.ltz.hardware.domain.entity;

import java.text.Normalizer;
import java.util.Locale;

/** Canonical spelling shared by brand and general-model uniqueness. */
public final class CatalogText {
    private CatalogText() { }

    public static String clean(String value, int limit) {
        if (value == null || value.length() > limit
                || value.codePoints().anyMatch(c -> Character.isISOControl(c) || c == '<' || c == '>')) {
            throw new IllegalArgumentException("Geçersiz katalog metni");
        }
        String cleaned = Normalizer.normalize(value, Normalizer.Form.NFKC).replaceAll("\\p{Z}+", " ").strip();
        if (cleaned.isBlank() || cleaned.length() > limit
                || cleaned.codePoints().anyMatch(c -> Character.isISOControl(c) || c == '<' || c == '>')) {
            throw new IllegalArgumentException("Geçersiz katalog metni");
        }
        return cleaned;
    }

    public static String normalize(String value, int limit) {
        String normalized = clean(value, limit).toLowerCase(Locale.ROOT);
        if (normalized.length() > limit) throw new IllegalArgumentException("Geçersiz katalog metni");
        return normalized;
    }
}
