package com.ltz.game.infrastructure.config;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Geliştirici ve admin medya bağlantıları için izinli kaynaklar (tek yönetim noktası: application.properties).
 * Her kayıt {@code host} veya {@code host/yol-öneki} biçimindedir: yalnız tam host eşleşir (alt alan adı joker karakteri yok);
 * yol öneki verilmişse bağlantı yolu bu önek altında olmalıdır. Liste boşsa o türde hiçbir bağlantı kabul edilmez.
 */
@ConfigurationProperties("ltz.game.media")
public record MediaUrlProperties(Set<String> imageSources, Set<String> videoSources, Set<String> demoSources) {
    public MediaUrlProperties {
        imageSources = clean(imageSources);
        videoSources = clean(videoSources);
        demoSources = clean(demoSources);
    }

    private static Set<String> clean(Set<String> sources) {
        if (sources == null) return Set.of();
        Set<String> cleaned = sources.stream().map(source -> source.strip().toLowerCase(Locale.ROOT)).filter(source -> !source.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        cleaned.forEach(MediaUrlProperties::requireWellFormed);
        return cleaned;
    }

    /** Yanlış yapılandırma sessizce geniş izin üretmesin: şema, kimlik bilgisi, port, joker veya nokta segmenti içeremez. */
    private static void requireWellFormed(String source) {
        int slash = source.indexOf('/');
        String host = slash < 0 ? source : source.substring(0, slash);
        String path = slash < 0 ? "" : source.substring(slash);
        if (host.isEmpty() || host.indexOf('.') < 0 || host.indexOf(':') >= 0 || host.indexOf('@') >= 0 || host.indexOf('*') >= 0
                || path.contains("..") || path.contains("//") || path.indexOf('%') >= 0 || path.indexOf('?') >= 0 || path.indexOf('#') >= 0)
            throw new IllegalStateException("Geçersiz medya kaynağı yapılandırması.");
    }
}
