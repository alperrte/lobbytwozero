package com.ltz.auth.application.port;

/**
 * Auth → Mail sözleşmesi (MAIL-02). Token'ı Auth üretir; Mail Service yalnız teslim eder. Teslim kabul edilemezse
 * {@link VerificationCodeDeliveryException} fırlatılmalıdır; sessizce başarılı dönmek yasaktır.
 */
public interface PasswordResetMailPort {

    void sendPasswordReset(PasswordResetMail mail);
}
