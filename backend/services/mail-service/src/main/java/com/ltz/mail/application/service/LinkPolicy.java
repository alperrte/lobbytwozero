package com.ltz.mail.application.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ltz.mail.domain.exception.MailErrorCode;
import com.ltz.mail.domain.exception.MailException;
import com.ltz.mail.infrastructure.config.LinkProperties;

/**
 * E-postaya konabilecek bağlantıların politikası (açık yönlendirme / kimlik avı yüzeyi): yalnız {@code https} (yerel
 * adreste {@code http}), kimlik bilgisi (kullanıcı:parola@) yok, en çok 512 karakter ve host izinli listede. İzinli liste
 * boşsa yalnız yerel adresler (localhost, 127.0.0.1) kabul edilir; üretimde liste boş olamaz (açılışta denetlenir).
 */
@Component
public class LinkPolicy {

    static final int MAX_LENGTH = 512;
    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");

    private final Set<String> allowedHosts;

    public LinkPolicy(LinkProperties properties) {
        this.allowedHosts = properties.allowedHosts().stream()
                .map(host -> host.strip().toLowerCase(Locale.ROOT))
                .filter(host -> !host.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** @throws MailException {@code INVALID_LINK} bağlantı kabul edilmezse */
    public void requireAllowed(String link) {
        if (link == null || link.isBlank() || link.length() > MAX_LENGTH || !isAllowed(link)) {
            throw new MailException(MailErrorCode.INVALID_LINK);
        }
    }

    private boolean isAllowed(String link) {
        URI uri;
        try {
            uri = new URI(link);
        } catch (URISyntaxException _) {
            return false;
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null || uri.getRawUserInfo() != null) {
            return false;
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        boolean loopback = LOOPBACK_HOSTS.contains(normalizedHost);
        boolean hostAllowed = allowedHosts.isEmpty() ? loopback : allowedHosts.contains(normalizedHost);
        if (!hostAllowed) {
            return false;
        }
        return "https".equals(scheme) || ("http".equals(scheme) && loopback);
    }
}
