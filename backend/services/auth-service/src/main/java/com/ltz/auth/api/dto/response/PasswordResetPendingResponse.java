package com.ltz.auth.api.dto.response;

/** Hesabın var olup olmadığını veya e-postanın gönderildiğini bildirmeyen tek tip yanıt. */
public record PasswordResetPendingResponse(String nextStep) {

    public static final PasswordResetPendingResponse CHECK_EMAIL = new PasswordResetPendingResponse("CHECK_EMAIL");
}
