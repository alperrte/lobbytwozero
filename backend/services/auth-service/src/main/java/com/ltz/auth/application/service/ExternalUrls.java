package com.ltz.auth.application.service;

import java.net.URI;

import org.springframework.stereotype.Component;

import com.ltz.auth.infrastructure.config.OAuthProperties;

import lombok.RequiredArgsConstructor;

/** Sağlayıcılara bildirilen, yapılandırmadan türetilen adresler (istemci girdisinden adres üretilmez). */
@Component
@RequiredArgsConstructor
public class ExternalUrls {

    private static final String TRAILING_SLASHES = "/+$";

    private final OAuthProperties properties;

    /** Dışarıdan görünen Gateway adresi + yol; Google redirect URI'si ve Steam return_to bunun üzerine kurulur. */
    public String publicUrl(String path) {
        return properties.publicBaseUrl().toString().replaceAll(TRAILING_SLASHES, "") + path;
    }

    /** Steam realm: return_to'nun kökü (şema + host + port). */
    public String realm() {
        URI base = properties.publicBaseUrl();
        return base.getScheme() + "://" + base.getRawAuthority();
    }
}
