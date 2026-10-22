package com.ltz.social.application.service;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Davet kodu üretimi ve kullanıcı girdisinin normalleştirilmesi. Kod, {@link SecureRandom} ile üretilen 10
 * karakterlik Crockford base32'dir (50 bit; I, L, O, U yoktur). Kod tahmin edilemez olmalıdır: bir kod, kod
 * sahibine arkadaşlık isteği başlatma yetkisi verir.
 */
@Component
public class InviteCodeGenerator {

    static final int LENGTH = 10;
    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final Pattern WELL_FORMED = Pattern.compile("^[0-9A-HJKMNP-TV-Z]{" + LENGTH + "}$");

    private final SecureRandom random = new SecureRandom();

    public String next() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int index = 0; index < LENGTH; index++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /**
     * Büyük harfe çevirir; boşluk ve tireleri atar; Crockford kuralıyla karışan karakterleri düzeltir
     * (I, L → 1; O → 0). Böylece "abcd-efgh-jk" gibi elle yazılmış kod da eşleşir.
     */
    static String normalize(String input) {
        return input.toUpperCase(Locale.ROOT)
                .replaceAll("[\\s-]", "")
                .replace('I', '1')
                .replace('L', '1')
                .replace('O', '0');
    }

    static boolean isWellFormed(String normalized) {
        return WELL_FORMED.matcher(normalized).matches();
    }
}
