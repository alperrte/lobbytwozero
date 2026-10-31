package com.ltz.mail.application.port;

import java.util.UUID;

/**
 * Kuyruktaki hassas içeriğin (doğrulama kodu, bağlantı) şifrelenmesi ve istek parmak izi. Şifre çözme başarısızsa
 * {@link PayloadCipherException} fırlatılır; yanlış anahtar veya bozuk veri hiçbir zaman düz içerik döndürmez.
 */
public interface PayloadCipherPort {

    /** Yeni şifrelemelerde kullanılan anahtar sürümü. */
    short currentKeyVersion();

    /** Kaydın kimliğine bağlı (AAD) şifreleme; başka kayda taşınan şifreli içerik çözülemez. */
    byte[] encrypt(UUID messageId, byte[] plain);

    byte[] decrypt(UUID messageId, short keyVersion, byte[] encrypted);

    /**
     * İstek içeriğinin anahtarlı parmak izi (64 karakterlik hex). Düz özet olmaz: 6 haneli kod gibi küçük uzaylı
     * değerler özetten kaba kuvvetle bulunamamalıdır.
     */
    String fingerprint(String canonicalRequest);

    /** Şifre çözme/doğrulama hatası; mesaj içerik veya anahtar bilgisi taşımaz. */
    class PayloadCipherException extends RuntimeException {

        public PayloadCipherException(String message) {
            super(message);
        }

        public PayloadCipherException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
