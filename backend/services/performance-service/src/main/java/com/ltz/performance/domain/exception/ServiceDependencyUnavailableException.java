package com.ltz.performance.domain.exception;

/**
 * Game/Hardware doğrulaması yapılamadığında kapalı başarısızlık: referans doğrulanamıyorsa kayıt oluşturulmaz
 * ve sahte bir "doğrulandı" sonucu üretilmez.
 */
public class ServiceDependencyUnavailableException extends RuntimeException {
    public static final String GAME_SERVICE_UNAVAILABLE = "GAME_SERVICE_UNAVAILABLE";
    public static final String HARDWARE_SERVICE_UNAVAILABLE = "HARDWARE_SERVICE_UNAVAILABLE";

    private final String code;

    public ServiceDependencyUnavailableException(String code) {
        super(code);
        this.code = code;
    }

    public String code() { return code; }
}
