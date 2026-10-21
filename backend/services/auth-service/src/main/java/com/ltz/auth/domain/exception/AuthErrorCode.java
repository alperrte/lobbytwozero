package com.ltz.auth.domain.exception;

/** Kimlik doğrulama akışlarının istemciye dönen hata sözleşmesi (HTTP durumu + kod + güvenli açıklama). */
public enum AuthErrorCode {
    INVALID_CREDENTIALS(401, "Invalid credentials."),
    EMAIL_NOT_VERIFIED(403, "Email address is not verified."),
    ACCOUNT_SUSPENDED(403, "Account is suspended."),
    ACCOUNT_BANNED(403, "Account is banned."),
    ACCOUNT_UNAVAILABLE(403, "Account is not available."),
    TWO_FACTOR_ENROLLMENT_REQUIRED(403, "Two-factor authentication must be enabled for this account."),
    TWO_FACTOR_REQUIRED_FOR_ROLE(403, "Two-factor authentication cannot be disabled for this account."),
    TOO_MANY_ATTEMPTS(429, "Too many attempts. Try again later."),
    SESSION_INVALID(401, "Session is invalid or has expired."),
    REFRESH_CONFLICT(409, "Session was refreshed concurrently. Retry the request."),
    MFA_FAILED(401, "Two-factor verification failed."),
    INVALID_TWO_FACTOR_CODE(400, "Two-factor code is invalid."),
    TWO_FACTOR_ALREADY_ENABLED(409, "Two-factor authentication is already enabled."),
    TWO_FACTOR_NOT_PENDING(409, "No pending two-factor setup."),
    TWO_FACTOR_NOT_ENABLED(409, "Two-factor authentication is not enabled."),
    EXTERNAL_AUTH_FAILED(401, "External authentication failed."),
    EXTERNAL_EMAIL_UNVERIFIED(403, "The provider has not verified the email address."),
    ACCOUNT_LINK_REQUIRED(409, "An account with this email already exists. Sign in and link the provider."),
    PROVIDER_NOT_CONFIGURED(503, "This sign-in method is not available."),
    REGISTRATION_TOKEN_INVALID(400, "Registration token is invalid or has expired."),
    CURRENT_PASSWORD_INVALID(400, "Current password is incorrect."),
    NEW_PASSWORD_SAME_AS_CURRENT(400, "New password must differ from the current password."),
    PASSWORD_RESET_TOKEN_INVALID(400, "Password reset link is invalid or has expired.");

    private final int httpStatus;
    private final String detail;

    AuthErrorCode(int httpStatus, String detail) {
        this.httpStatus = httpStatus;
        this.detail = detail;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String detail() {
        return detail;
    }
}
