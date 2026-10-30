package com.ltz.user.application.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.ltz.user.domain.entity.UserProfile;
import com.ltz.user.domain.enums.PlayerType;
import com.ltz.user.domain.exception.UserErrorCode;
import com.ltz.user.domain.exception.UserException;

/**
 * Profil metni ve liste alanlarının sunucu tarafı doğrulaması (SECURITY.md §5: UGC güvenilmezdir). Metinler Unicode
 * NFC'ye çevrilir; denetim, biçim (bidi/sıfır genişlik), özel kullanım ve atanmamış karakterler reddedilir. Metin düz
 * metin olarak saklanır (HTML çevrilmez); istemci onu yalnız metin olarak göstermelidir.
 */
final class ProfileText {

    static final int DISPLAY_NAME_MIN = 2;
    static final int DISPLAY_NAME_MAX = 32;
    static final int BIO_MAX = 500;
    static final int LIST_MAX = 10;
    static final int SEARCH_MIN = 2;

    private static final Pattern CODE = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,47}$");
    private static final Pattern SPACE_RUN = Pattern.compile(" {2,}");
    private static final Set<String> COUNTRIES = Set.of(Locale.getISOCountries());

    private ProfileText() {
    }

    /** @return normalize görünen ad, boş girdide {@code null} (alan temizlenir) */
    static String displayName(String raw) {
        String text = clean(raw, false);
        if (text == null) {
            return null;
        }
        text = SPACE_RUN.matcher(text).replaceAll(" ");
        int length = text.codePointCount(0, text.length());
        if (length < DISPLAY_NAME_MIN || length > DISPLAY_NAME_MAX) {
            throw new UserException(UserErrorCode.INVALID_DISPLAY_NAME);
        }
        return text;
    }

    /** @return normalize biyografi, boş girdide {@code null}; yalnız satır sonu (\n) ek denetim karakteridir */
    static String bio(String raw) {
        String text = clean(raw, true);
        if (text == null) {
            return null;
        }
        if (text.codePointCount(0, text.length()) > BIO_MAX) {
            throw new UserException(UserErrorCode.INVALID_BIO);
        }
        return text;
    }

    /** @return büyük harfli ISO 3166-1 alpha-2 kodu, boş girdide {@code null} */
    static String country(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String code = raw.strip().toUpperCase(Locale.ROOT);
        if (!COUNTRIES.contains(code)) {
            throw new UserException(UserErrorCode.INVALID_COUNTRY);
        }
        return code;
    }

    /** En çok {@link PlayerType#MAX_PER_PROFILE} bilinen oyuncu türü; yinelenenler birleştirilir. */
    static List<String> playerTypes(List<String> raw) {
        Set<String> types = new LinkedHashSet<>();
        for (String value : raw) {
            String name = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
            if (PlayerType.parse(name).isEmpty()) {
                throw new UserException(UserErrorCode.INVALID_PLAYER_TYPES);
            }
            types.add(name);
        }
        if (types.size() > PlayerType.MAX_PER_PROFILE) {
            throw new UserException(UserErrorCode.INVALID_PLAYER_TYPES);
        }
        return new ArrayList<>(types);
    }

    static List<String> favoriteGenres(List<String> raw) {
        return codes(raw, UserErrorCode.INVALID_FAVORITE_GENRES);
    }

    static List<String> favoritePlatforms(List<String> raw) {
        return codes(raw, UserErrorCode.INVALID_FAVORITE_PLATFORMS);
    }

    /** Katalog kodları: küçük harf, rakam, {@code _} ve {@code -}; en çok {@link #LIST_MAX} benzersiz kod. */
    private static List<String> codes(List<String> raw, UserErrorCode error) {
        Set<String> codes = new LinkedHashSet<>();
        for (String value : raw) {
            String code = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
            if (!CODE.matcher(code).matches()) {
                throw new UserException(error);
            }
            codes.add(code);
        }
        if (codes.size() > LIST_MAX) {
            throw new UserException(error);
        }
        return new ArrayList<>(codes);
    }

    /**
     * Arama sorgusunu LIKE önekine çevirir: NFC, kırpma, profil anahtarlarıyla aynı küçük harf ({@code UserProfile.normalize}),
     * 2-32 kod noktası, yasak karakter yok; {@code \ % _} kaçırılır ve sona {@code %} eklenir.
     */
    static String searchPrefix(String raw) {
        String text;
        try {
            text = clean(raw, false);
        } catch (UserException _) {
            throw new UserException(UserErrorCode.INVALID_SEARCH_QUERY);
        }
        if (text == null) {
            throw new UserException(UserErrorCode.INVALID_SEARCH_QUERY);
        }
        String normalized = UserProfile.normalize(text);
        int length = normalized.codePointCount(0, normalized.length());
        if (length < SEARCH_MIN || length > DISPLAY_NAME_MAX) {
            throw new UserException(UserErrorCode.INVALID_SEARCH_QUERY);
        }
        return normalized.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    /** NFC + kırpma; yasak karakter varsa hata. Boş sonuç {@code null} döner. */
    private static String clean(String raw, boolean allowNewline) {
        if (raw == null) {
            return null;
        }
        String text = Normalizer.normalize(raw, Normalizer.Form.NFC);
        if (allowNewline) {
            text = text.replace("\r\n", "\n").replace('\r', '\n');
        }
        text = text.strip();
        if (text.isEmpty()) {
            return null;
        }
        UserErrorCode error = allowNewline ? UserErrorCode.INVALID_BIO : UserErrorCode.INVALID_DISPLAY_NAME;
        if (text.codePoints().anyMatch(point -> forbidden(point, allowNewline))) {
            throw new UserException(error);
        }
        return text;
    }

    private static boolean forbidden(int codePoint, boolean allowNewline) {
        if (codePoint == '\n') {
            return !allowNewline;
        }
        if (codePoint == ' ') {
            return false;
        }
        return switch (Character.getType(codePoint)) {
            case Character.CONTROL, Character.FORMAT, Character.PRIVATE_USE, Character.SURROGATE,
                    Character.UNASSIGNED, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR,
                    Character.SPACE_SEPARATOR -> true;
            default -> false;
        };
    }
}
