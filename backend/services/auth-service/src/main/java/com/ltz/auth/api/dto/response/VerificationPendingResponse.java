package com.ltz.auth.api.dto.response;

/**
 * Kayıt ve yeniden gönderme için tek tip yanıt. Hesabın var olup olmadığını veya e-postanın
 * gerçekten gönderildiğini bildirmez (K1, K5).
 */
public record VerificationPendingResponse(String nextStep) {

    public static final VerificationPendingResponse VERIFY_EMAIL = new VerificationPendingResponse("VERIFY_EMAIL");
}
