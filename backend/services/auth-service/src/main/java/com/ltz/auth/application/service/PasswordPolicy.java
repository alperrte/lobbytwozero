package com.ltz.auth.application.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.ltz.auth.domain.exception.AuthErrorCode;
import com.ltz.auth.domain.exception.AuthException;

/**
 * Parola politikası (NIST SP 800-63B-4): uzunluk bean validation'da (en az 15), burada ise tahmin edilmesi kolay
 * parolalar reddedilir. Yalnız parola BELİRLENİRKEN (kayıt, değiştirme, sıfırlama) uygulanır; giriş denemesine
 * uygulanmaz, böylece eski parolalı hesaplar etkilenmez. Karşılaştırma küçük harf ve yalnız harf+rakam üzerinden yapılır.
 * <p>
 * Reddedilenler: yerel yaygın parola listesi, kısa bir birimin tekrarı, düz ardışık dizi, hesabın kullanıcı adını veya
 * e-posta yerel kısmını içeren parola ve servis adı. Sızmış parola veritabanı sorgusu bu sürümde yoktur.
 */
@Component
public class PasswordPolicy {

    static final String BLOCKLIST_RESOURCE = "/password-blocklist.txt";
    private static final int MIN_IDENTITY_FRAGMENT = 4;
    private static final int MAX_REPEAT_UNIT = 8;
    private static final String SERVICE_NAME = "lobbytwozero";

    private final Set<String> blocklist;

    public PasswordPolicy() {
        this.blocklist = loadBlocklist();
    }

    /** Zayıfsa {@link AuthErrorCode#WEAK_PASSWORD} ile {@link AuthException} fırlatır; parola hiçbir yere yazılmaz. */
    public void require(String password, String email, String username) {
        if (isWeak(password, email, username)) {
            throw new AuthException(AuthErrorCode.WEAK_PASSWORD);
        }
    }

    boolean isWeak(String password, String email, String username) {
        String normalized = normalize(password);
        return blocklist.contains(normalized)
                || normalized.contains(SERVICE_NAME)
                || isRepeatedUnit(normalized)
                || isSequential(normalized)
                || containsFragment(normalized, username)
                || containsFragment(normalized, localPart(email));
    }

    private static boolean containsFragment(String normalizedPassword, String identity) {
        if (identity == null) {
            return false;
        }
        String fragment = normalize(identity);
        return fragment.length() >= MIN_IDENTITY_FRAGMENT && normalizedPassword.contains(fragment);
    }

    private static String localPart(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    /** "abcabcabc...", "aaaaaaaa..." gibi en çok {@value #MAX_REPEAT_UNIT} karakterlik birimin tekrarı. */
    private static boolean isRepeatedUnit(String value) {
        if (value.length() < 2) {
            return false;
        }
        int period = (value + value).indexOf(value, 1);
        return period < value.length() && period <= MAX_REPEAT_UNIT;
    }

    /** Tüm karakterleri +1 veya -1 adımla ilerleyen rakam (9→0 sarar) ya da harf dizisi. */
    private static boolean isSequential(String value) {
        if (value.length() < 2) {
            return false;
        }
        boolean digits = value.chars().allMatch(Character::isDigit);
        boolean letters = value.chars().allMatch(Character::isLetter);
        if (!digits && !letters) {
            return false;
        }
        int step = delta(value.charAt(0), value.charAt(1), digits);
        if (step != 1 && step != -1) {
            return false;
        }
        for (int i = 2; i < value.length(); i++) {
            if (delta(value.charAt(i - 1), value.charAt(i), digits) != step) {
                return false;
            }
        }
        return true;
    }

    private static int delta(char previous, char current, boolean digits) {
        int raw = current - previous;
        if (!digits) {
            return raw;
        }
        int wrapped = Math.floorMod(raw, 10);
        return wrapped == 1 ? 1 : wrapped == 9 ? -1 : 0;
    }

    static String normalize(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        value.toLowerCase(Locale.ROOT).chars()
                .filter(Character::isLetterOrDigit)
                .forEach(builder::appendCodePoint);
        return builder.toString();
    }

    private static Set<String> loadBlocklist() {
        try (InputStream stream = PasswordPolicy.class.getResourceAsStream(BLOCKLIST_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Password blocklist resource is missing: " + BLOCKLIST_RESOURCE);
            }
            Set<String> entries = new HashSet<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String entry = line.strip();
                    if (!entry.isEmpty() && !entry.startsWith("#")) {
                        entries.add(normalize(entry));
                    }
                }
            }
            return Set.copyOf(entries);
        } catch (IOException exception) {
            throw new UncheckedIOException("Password blocklist could not be read", exception);
        }
    }
}
