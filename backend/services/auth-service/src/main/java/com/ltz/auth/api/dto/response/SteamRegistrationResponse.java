package com.ltz.auth.api.dto.response;

/**
 * Steam ile yeni hesap sonucu. verificationProof, e-posta doğrulamasında kayıt sahibinin kendisi olduğunu kanıtlar ve
 * yalnız kayıt token'ını tutana verilir; kayıtlı e-posta durumunda da aynı biçimde (kullanılamaz bir) değer döner.
 */
public record SteamRegistrationResponse(String nextStep, String verificationProof) {

    @Override
    public String toString() {
        return "SteamRegistrationResponse[nextStep=" + nextStep + ", verificationProof=***]";
    }
}
