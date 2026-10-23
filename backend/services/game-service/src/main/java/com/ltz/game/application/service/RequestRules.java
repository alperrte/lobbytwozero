package com.ltz.game.application.service;

import com.ltz.game.domain.exception.InvalidGameRequestException;
import java.util.regex.Pattern;

/** Kullanıcı girdisi için ortak sunucu tarafı kurallar. Metinler düz metindir; HTML işlenmez, istemci kaçışlı gösterir. */
final class RequestRules {
    static final Pattern TERM_CODE = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");
    static final Pattern LANGUAGE = Pattern.compile("[a-z]{2,3}(-[a-z0-9]{2,8}){0,3}");
    private RequestRules() { }

    /** Moderasyon ve yönetim işlemlerinde gerekçe zorunlu ve en az 8 karakterdir. */
    static String reason(String value) {
        String text = value == null ? "" : value.strip();
        if (text.length() < 8 || text.length() > 1000 || hasControl(text)) throw new InvalidGameRequestException("REASON_REQUIRED");
        return text;
    }

    /** Boş olmayan, kontrol karakteri içermeyen (satır sonu ve sekme hariç) düz metin. */
    static String text(String value, int max, String code) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty() || text.length() > max || hasControl(text)) throw new InvalidGameRequestException(code);
        return text;
    }

    /** Boş metin null sayılır; doluysa {@link #text} kurallarına uyar. */
    static String optionalText(String value, int max, String code) {
        return value == null || value.isBlank() ? null : text(value, max, code);
    }

    private static boolean hasControl(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') return true;
        }
        return false;
    }
}
