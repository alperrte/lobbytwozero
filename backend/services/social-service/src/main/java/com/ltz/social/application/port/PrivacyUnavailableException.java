package com.ltz.social.application.port;

/** Gizlilik tercihi alınamadı (user-service ulaşılamaz ya da henüz uç sunmuyor). Mesaj yalnız güvenli neden içerir. */
public class PrivacyUnavailableException extends RuntimeException {

    public PrivacyUnavailableException(String message) {
        super(message);
    }
}
