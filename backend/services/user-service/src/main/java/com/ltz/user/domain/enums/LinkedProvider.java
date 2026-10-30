package com.ltz.user.domain.enums;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Desteklenen dış hesap sağlayıcıları (K8: yalnız Steam; Discord kapsam dışı). DB'deki CHECK ile aynı küme. */
public enum LinkedProvider {
    STEAM(Pattern.compile("^[0-9]{17}$"), "https://steamcommunity.com/profiles/");

    private final Pattern subjectFormat;
    private final String profileUrlPrefix;

    LinkedProvider(Pattern subjectFormat, String profileUrlPrefix) {
        this.subjectFormat = subjectFormat;
        this.profileUrlPrefix = profileUrlPrefix;
    }

    public boolean isValidSubject(String subject) {
        return subjectFormat.matcher(subject).matches();
    }

    /** Sağlayıcıdaki herkese açık profil adresi; kimlik biçimi doğrulanmış olmalıdır. */
    public String profileUrl(String subject) {
        return profileUrlPrefix + subject;
    }

    /** Büyük/küçük harfe duyarsız; bilinmeyen sağlayıcı boş döner (olaylarda yok sayılır, uçlarda 404). */
    public static Optional<LinkedProvider> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String name = raw.strip().toUpperCase(Locale.ROOT);
        for (LinkedProvider provider : values()) {
            if (provider.name().equals(name)) {
                return Optional.of(provider);
            }
        }
        return Optional.empty();
    }
}
