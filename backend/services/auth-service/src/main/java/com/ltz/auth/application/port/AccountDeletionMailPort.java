package com.ltz.auth.application.port;

/**
 * Auth → Mail sözleşmesi: hesap silme onay bağlantısı. Token'ı Auth üretir; Mail Service yalnız teslim eder.
 * Teslim kabul edilemezse {@link VerificationCodeDeliveryException} fırlatılmalıdır; sessizce başarılı dönmek yasaktır.
 */
public interface AccountDeletionMailPort {

    void sendAccountDeletionConfirmation(AccountDeletionMail mail);
}
