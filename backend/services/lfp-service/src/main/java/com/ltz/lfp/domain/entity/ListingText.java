package com.ltz.lfp.domain.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.ltz.lfp.domain.exception.LfpErrorCode;
import com.ltz.lfp.domain.exception.LfpException;

/**
 * Kullanıcı metni (UGC) kuralları (SECURITY.md §5): baştaki/sondaki boşluk atılır, uzunluk karakter (code point) olarak
 * sayılır ve kontrol karakterleri reddedilir. Açıklamada yalnız satır sonu ve sekmeye izin verilir; diğer alanlar tek
 * satırdır. Metin HTML olarak işlenmez, yalnız düz metin saklanır.
 */
public final class ListingText {

    public static final int MAX_DESCRIPTION = 1000;
    public static final int MAX_SKILL_LEVEL = 50;
    public static final int MAX_ROLE = 30;
    public static final int MAX_ROLES = 5;
    public static final int MAX_GAME_TITLE = 500;

    private static final int ZERO_WIDTH_JOINER = 0x200D;

    private ListingText() {
    }

    /** LFP-09: zorunlu, 1–1000 karakter; Windows satır sonları {@code \n}'e çevrilir. */
    public static String description(String raw) {
        String value = raw == null ? "" : raw.replace("\r\n", "\n").strip();
        if (!lengthBetween(value, MAX_DESCRIPTION) || hasControl(value, true)) {
            throw new LfpException(LfpErrorCode.INVALID_DESCRIPTION);
        }
        return value;
    }

    /** LFP-04: isteğe bağlı; boş ya da yalnız boşluksa {@code null} (belirtilmedi). */
    public static String skillLevel(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.strip();
        if (!lengthBetween(value, MAX_SKILL_LEVEL) || hasControl(value, false)) {
            throw new LfpException(LfpErrorCode.INVALID_SKILL_LEVEL);
        }
        return value;
    }

    /**
     * LFP-05: 1–5 rol, her biri 1–30 karakter. Büyük/küçük harf farkıyla tekrarlanan rol bir kez tutulur (ilk yazım
     * korunur); sıra korunur.
     */
    public static List<String> roles(List<String> raw) {
        if (raw == null || raw.isEmpty() || raw.size() > MAX_ROLES) {
            throw new LfpException(LfpErrorCode.INVALID_ROLES);
        }
        List<String> roles = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String role : raw) {
            String value = role == null ? "" : role.strip();
            if (!lengthBetween(value, MAX_ROLE) || hasControl(value, false)) {
                throw new LfpException(LfpErrorCode.INVALID_ROLES);
            }
            if (seen.add(value.toLowerCase(Locale.ROOT))) {
                roles.add(value);
            }
        }
        return List.copyOf(roles);
    }

    /** Rol araması büyük/küçük harfe duyarsızdır; DB'deki {@code lower(role)} ile aynı biçim. */
    public static String roleKey(String role) {
        return role.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * game-service'ten gelen oyun adı da güvenilmez girdidir: boşluk atılır, kontrol karakterleri boşluğa çevrilir ve
     * en fazla 500 karakter tutulur. Boş ad kabul edilmez ({@code null} döner).
     */
    public static String gameTitle(String raw) {
        if (raw == null) {
            return null;
        }
        StringBuilder cleaned = new StringBuilder();
        raw.codePoints().forEach(codePoint -> cleaned.appendCodePoint(isControl(codePoint) ? ' ' : codePoint));
        String value = cleaned.toString().strip();
        if (value.isEmpty()) {
            return null;
        }
        if (value.codePointCount(0, value.length()) > MAX_GAME_TITLE) {
            value = value.substring(0, value.offsetByCodePoints(0, MAX_GAME_TITLE)).strip();
        }
        return value;
    }

    private static boolean lengthBetween(String value, int max) {
        int length = value.codePointCount(0, value.length());
        return length >= 1 && length <= max;
    }

    private static boolean hasControl(String value, boolean multiline) {
        return value.codePoints().anyMatch(codePoint -> {
            if (multiline && (codePoint == '\n' || codePoint == '\t')) {
                return false;
            }
            return isControl(codePoint);
        });
    }

    /**
     * ISO kontrol karakterleri ve görünmez biçim karakterleri (ör. yön değiştirme işaretleri, sıfır genişlikli boşluk).
     * Emoji dizilerini birleştiren sıfır genişlikli birleştiriciye (U+200D) izin verilir.
     */
    private static boolean isControl(int codePoint) {
        return Character.isISOControl(codePoint)
                || (Character.getType(codePoint) == Character.FORMAT && codePoint != ZERO_WIDTH_JOINER);
    }
}
