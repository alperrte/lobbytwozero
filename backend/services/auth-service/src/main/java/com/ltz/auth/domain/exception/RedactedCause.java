package com.ltz.auth.domain.exception;

/**
 * Orijinal hatanın <b>yığın izini</b> taşır, mesajını ve neden zincirini taşımaz. Sağlayıcı, sürücü ve SMTP hata
 * mesajları alıcı e-postası, yanıt gövdesi veya kayıt değeri içerebilir; beklenmeyen hata günlüğü tüm neden zincirini
 * yazdığı için ham nedeni eklemek kişisel veri sızdırırdı. Bu sarmalayıcıyla hatanın nereden geldiği izlenir
 * (hata türü ve çağrı yığını), içeriği taşınmaz.
 */
public final class RedactedCause extends RuntimeException {

    private RedactedCause(String originalType) {
        super(originalType, null, false, true);
    }

    public static RedactedCause of(Throwable original) {
        RedactedCause cause = new RedactedCause(original.getClass().getName());
        cause.setStackTrace(original.getStackTrace());
        return cause;
    }
}
