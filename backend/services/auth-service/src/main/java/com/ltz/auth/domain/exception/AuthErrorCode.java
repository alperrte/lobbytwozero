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
    WEAK_PASSWORD(400, "Password is too easy to guess. Choose a different one."),
    PASSWORD_RESET_TOKEN_INVALID(400, "Password reset link is invalid or has expired."),
    ACCOUNT_NOT_FOUND(404, "Account was not found."),
    INVALID_STATUS_TRANSITION(409, "The requested account status transition is not allowed."),
    SELF_MODIFICATION_FORBIDDEN(403, "Administrators cannot change their own status or roles."),
    TARGET_NOT_ACTIVE(409, "The target account is not active."),
    ROLE_ALREADY_ASSIGNED(409, "The role is already assigned."),
    ROLE_NOT_ASSIGNED(409, "The role is not assigned."),
    BASE_ROLE_REQUIRED(409, "The base player role cannot be revoked."),
    ROLE_REQUIRES_TWO_FACTOR(409, "The target account must enable two-factor authentication before receiving this role."),
    REAUTHENTICATION_REQUIRED(403, "Recent sign-in is required. Sign in again and retry."),
    ADMIN_DELETION_FORBIDDEN(409, "Administrator accounts cannot be deleted. Remove the administrator role first."),
    DELETION_CONFIRMATION_INVALID(400, "Deletion confirmation is invalid or has expired."),
    ACCOUNT_DELETION_PENDING(403, "Account deletion is scheduled. Cancel it before the waiting period ends to keep the account."),
    INVALID_CLIENT(401, "Service client authentication failed."),
    SERVICE_AUDIENCE_FORBIDDEN(403, "The service client is not allowed to obtain a token for this audience."),
    SERVICE_ACCOUNT_BINDING_INVALID(400, "This audience requires exactly one target account; other audiences take none."),
    SERVICE_TARGET_ACCOUNT_UNAVAILABLE(403, "The target account is not active."),
    ACTING_ADMIN_INVALID(403, "The acting administrator is not an active administrator."),
    EMAIL_CHANGE_INVALID(400, "Email change code is invalid or has expired."),
    PASSWORD_REQUIRED(409, "A password must be set on this account before this action."),
    EXTERNAL_IDENTITY_TAKEN(409, "This external identity is already linked to another account."),
    EXTERNAL_IDENTITY_ALREADY_LINKED(409, "This account already has an identity from this provider."),
    EXTERNAL_IDENTITY_NOT_LINKED(404, "No identity from this provider is linked to this account."),
    LAST_LOGIN_METHOD(409, "The last remaining way to sign in cannot be removed."),
    DEVICE_NOT_FOUND(404, "The device was not found."),
    DEVICE_LIMIT_REACHED(409, "Too many active devices. Remove one before binding another."),
    INVALID_DEVICE_CREDENTIALS(401, "Device authentication failed."),
    DELETION_NOT_PENDING(409, "No cancellable account deletion exists for this account."),
    DELETION_CANCEL_WINDOW_CLOSED(409, "The waiting period has ended; the deletion can no longer be cancelled.");

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
