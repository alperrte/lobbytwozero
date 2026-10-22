package com.ltz.auth.application.port;

/**
 * Auth → Mail sözleşmesi (MAIL-01). Kodu Auth üretir; Mail Service yalnız teslim eder.
 * Uygulama, teslimi kabul edemediğinde {@link VerificationCodeDeliveryException} fırlatmalıdır;
 * sessizce başarılı dönmek yasaktır.
 */
public interface VerificationCodeMailPort {

    void sendVerificationCode(VerificationCodeMail mail);
}
