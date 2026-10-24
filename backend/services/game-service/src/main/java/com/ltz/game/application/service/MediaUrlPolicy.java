package com.ltz.game.application.service;

import com.ltz.game.domain.enums.DeveloperMediaType;
import com.ltz.game.domain.exception.InvalidGameRequestException;
import com.ltz.game.infrastructure.config.MediaUrlProperties;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Medya bağlantısı doğrulaması. Yalnız bağlantı metni incelenir; sunucu bu adreslere hiçbir zaman istek atmaz
 * (SSRF yok) ve yönlendirme izlemez. HTTPS, kullanıcı bilgisi/port/fragment yok, IP yok, ASCII RFC 3986 karakterleri,
 * güvenli yol (nokta segmenti, çift eğik çizgi, yüzde kodlaması yok), tür bazlı tam-host ve yol öneki allowlist'i ve
 * görsellerde görüntü dosyası uzantısı zorunludur.
 */
@Component
@EnableConfigurationProperties(MediaUrlProperties.class)
public class MediaUrlPolicy {
    static final int MAX_LENGTH = 1024;
    private static final Pattern ALLOWED_CHARACTERS = Pattern.compile("[A-Za-z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+");
    private static final List<String> IMAGE_EXTENSIONS = List.of(".jpg", ".jpeg", ".png", ".webp", ".gif");
    private final MediaUrlProperties properties;

    public MediaUrlPolicy(MediaUrlProperties properties) { this.properties = properties; }

    public String validate(DeveloperMediaType type, String value) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty() || text.length() > MAX_LENGTH || !ALLOWED_CHARACTERS.matcher(text).matches()) throw invalid();
        URI uri;
        try {
            uri = new URI(text);
        } catch (URISyntaxException _) {
            throw invalid();
        }
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getRawUserInfo() != null
                || uri.getPort() != -1 || uri.getRawFragment() != null) throw invalid();
        host = host.toLowerCase(Locale.ROOT);
        String path = uri.getRawPath();
        if (!validHost(host) || !safePath(path) || !allowed(sources(type), host, path)
                || (type == DeveloperMediaType.IMAGE && !hasImageExtension(path))) throw invalid();
        return text;
    }

    /** DNS adı: en az iki etiket, etiket başına 1-63 küçük harf/rakam/tire (başta ve sonda tire yok), son etiket yalnız rakam değil (IP yok). */
    private static boolean validHost(String host) {
        String[] labels = host.split("\\.", -1);
        if (labels.length < 2) return false;
        for (String label : labels) {
            if (label.isEmpty() || label.length() > 63 || label.charAt(0) == '-' || label.charAt(label.length() - 1) == '-') return false;
            if (!label.chars().allMatch(MediaUrlPolicy::hostCharacter)) return false;
        }
        return !labels[labels.length - 1].chars().allMatch(Character::isDigit);
    }

    private static boolean hostCharacter(int c) { return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-'; }

    /** Yol mutlak olmalı; yüzde kodlaması, noktalı virgül, boş segment ve nokta segmentleri (yol atlatma) reddedilir. */
    private static boolean safePath(String path) {
        if (path == null || path.isEmpty() || path.charAt(0) != '/') return false;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (!(Character.isLetterOrDigit(c) && c < 128) && "._~-/@".indexOf(c) < 0) return false;
        }
        for (String segment : path.substring(1).split("/", -1)) {
            if (segment.equals(".") || segment.equals("..")) return false;
        }
        return !path.contains("//");
    }

    private static boolean hasImageExtension(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return IMAGE_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private static boolean allowed(Set<String> sources, String host, String path) {
        for (String source : sources) {
            int slash = source.indexOf('/');
            if (!(slash < 0 ? source : source.substring(0, slash)).equals(host)) continue;
            if (slash < 0 || underPrefix(path, source.substring(slash))) return true;
        }
        return false;
    }

    /** "/x/" öneki alt yolu ister; "/watch" öneki yalnız tam eşleşme veya "/watch/…" kabul eder ("/watchfoo" kabul edilmez). */
    private static boolean underPrefix(String path, String prefix) {
        if (prefix.endsWith("/")) return path.startsWith(prefix) && path.length() > prefix.length();
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private Set<String> sources(DeveloperMediaType type) {
        return switch (type) {
            case IMAGE -> properties.imageSources();
            case VIDEO -> properties.videoSources();
            case DEMO -> properties.demoSources();
        };
    }

    private static InvalidGameRequestException invalid() { return new InvalidGameRequestException("INVALID_MEDIA_URL"); }
}
