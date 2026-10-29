package com.ltz.social.application.port;

/** Olay tüketiciye teslim edilemedi. Mesajı yalnız güvenli bir neden içerir (yük, token, adres yok). */
public class EventDeliveryException extends RuntimeException {

    public EventDeliveryException(String message) {
        super(message);
    }

    /** Neden zincirde kalır ama loglanmaz: relay yalnız {@link #getMessage()} yazar. */
    public EventDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
