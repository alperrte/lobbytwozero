package com.ltz.mail.infrastructure.config;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Platform kapısı: Mail'e bilgilendirme maili attırabilen servisler (çağıran izin tablosu). Her giriş bir servis adıdır
 * ({@code svc:<ad>} token'ının {@code sub} değeri) ve kendi hız sınırlarını taşır. Liste BOŞ ise platform kapısı
 * kapalıdır (fail-closed): hiçbir servis token'ı kabul edilmez. Servisin ayrıca Auth'ta {@code mail:notify} hedefi
 * tanımlı olmalıdır; iki taraf birbirini doğrular.
 *
 * <pre>
 * ltz.mail.platform.callers.social-service.account-per-hour=5
 * ltz.mail.platform.callers.social-service.caller-per-hour=500
 * </pre>
 */
@Validated
@ConfigurationProperties("ltz.mail.platform")
public record PlatformProperties(Map<String, Caller> callers) {

    private static final Pattern CALLER_NAME = Pattern.compile("[a-z][a-z0-9-]{1,38}");
    private static final String SUBJECT_PREFIX = "svc:";

    public PlatformProperties {
        callers = callers == null ? Map.of() : Map.copyOf(callers);
        callers.keySet().forEach(name -> {
            if (!CALLER_NAME.matcher(name).matches() || "auth-service".equals(name)) {
                throw new IllegalArgumentException("Invalid platform caller name: " + name);
            }
        });
    }

    /** Bu {@code svc:<ad>} subject'inin izin satırı; {@code null} ise çağıran kayıtlı değildir. */
    public Caller callerFor(String subject) {
        return subject == null || !subject.startsWith(SUBJECT_PREFIX)
                ? null
                : callers.get(subject.substring(SUBJECT_PREFIX.length()));
    }

    /** Token doğrulamasında kabul edilen platform subject'leri ({@code svc:<ad>}). */
    public Set<String> subjects() {
        return callers.keySet().stream().map(name -> SUBJECT_PREFIX + name).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * @param accountPerHour bu çağıranın aynı hesaba saatte en çok kaç mail yaratabileceği (varsayılan 5)
     * @param callerPerHour  bu çağıranın toplam saatte en çok kaç mail yaratabileceği (varsayılan 500)
     */
    public record Caller(Integer accountPerHour, Integer callerPerHour) {

        static final int DEFAULT_ACCOUNT_PER_HOUR = 5;
        static final int DEFAULT_CALLER_PER_HOUR = 500;

        public Caller {
            accountPerHour = accountPerHour == null ? DEFAULT_ACCOUNT_PER_HOUR : accountPerHour;
            callerPerHour = callerPerHour == null ? DEFAULT_CALLER_PER_HOUR : callerPerHour;
            if (accountPerHour < 1 || callerPerHour < 1) {
                throw new IllegalArgumentException("Platform caller limits must be positive");
            }
        }
    }
}
