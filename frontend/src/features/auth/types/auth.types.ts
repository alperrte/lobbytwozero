/** Auth API tipleri (kaynak: docs/api.md → AUTH). Token hiçbir yerde tutulmaz; yalnız bu özetler bellekte durur. */

/** Sunucunun `code` alanı; bilinmeyen kod `UNKNOWN`'a düşer, ağ hatası `NETWORK` olur. */
export type AuthErrorCode =
  | "INVALID_CREDENTIALS"
  | "EMAIL_NOT_VERIFIED"
  | "ACCOUNT_SUSPENDED"
  | "ACCOUNT_BANNED"
  | "ACCOUNT_UNAVAILABLE"
  | "ACCOUNT_DELETION_PENDING"
  | "TWO_FACTOR_ENROLLMENT_REQUIRED"
  | "CSRF_TOKEN_INVALID"
  | "TOO_MANY_ATTEMPTS"
  | "SERVICE_BUSY"
  | "USERNAME_TAKEN"
  | "WEAK_PASSWORD"
  | "PASSWORD_RESET_TOKEN_INVALID"
  | "REGISTRATION_TOKEN_INVALID"
  | "NEW_PASSWORD_SAME_AS_CURRENT"
  | "VERIFICATION_FAILED"
  | "VALIDATION_FAILED"
  | "MALFORMED_REQUEST"
  | "PAYLOAD_TOO_LARGE"
  | "SESSION_INVALID"
  | "REFRESH_CONFLICT"
  | "MFA_NOT_SUPPORTED"
  | "MFA_FAILED"
  | "DELETION_CONFIRMATION_INVALID"
  | "ADMIN_DELETION_FORBIDDEN"
  | "INVALID_TWO_FACTOR_CODE"
  | "NETWORK"
  | "UNKNOWN";

export const knownAuthErrorCodes: readonly AuthErrorCode[] = [
  "INVALID_CREDENTIALS",
  "EMAIL_NOT_VERIFIED",
  "ACCOUNT_SUSPENDED",
  "ACCOUNT_BANNED",
  "ACCOUNT_UNAVAILABLE",
  "ACCOUNT_DELETION_PENDING",
  "TWO_FACTOR_ENROLLMENT_REQUIRED",
  "CSRF_TOKEN_INVALID",
  "TOO_MANY_ATTEMPTS",
  "SERVICE_BUSY",
  "USERNAME_TAKEN",
  "WEAK_PASSWORD",
  "PASSWORD_RESET_TOKEN_INVALID",
  "REGISTRATION_TOKEN_INVALID",
  "NEW_PASSWORD_SAME_AS_CURRENT",
  "VERIFICATION_FAILED",
  "VALIDATION_FAILED",
  "MALFORMED_REQUEST",
  "PAYLOAD_TOO_LARGE",
  "SESSION_INVALID",
  "REFRESH_CONFLICT",
  "MFA_NOT_SUPPORTED",
  "MFA_FAILED",
  "DELETION_CONFIRMATION_INVALID",
  "ADMIN_DELETION_FORBIDDEN",
  "INVALID_TWO_FACTOR_CODE",
  "NETWORK",
  "UNKNOWN"
];

/** `POST /login` yanıtı. Çerez yalnız `AUTHENTICATED` durumunda yazılır. */
export type LoginResult =
  | { status: "AUTHENTICATED"; accessTokenExpiresAt: string }
  | {
      status: "MFA_REQUIRED" | "MFA_ENROLLMENT_REQUIRED";
      challengeId: string;
      challengeExpiresAt: string;
    };

/** `GET /me` yanıtı: yalnız doğrulanmış token'daki bilgi. Roller arayüz içindir, yetki kararı sunucudadır. */
export type AuthMe = {
  accountId: string;
  roles: string[];
  authMethods: string[];
  accessTokenExpiresAt: string;
};

/** Tek kullanımlık MFA challenge'ı (bellekte tutulur; URL'ye, depoya ya da günlüğe yazılmaz). */
export type MfaChallenge = { kind: "verify" | "enroll"; id: string };

/** Kurulum başlangıcı: TOTP gizli anahtarı yalnız bu yanıtta görünür. */
export type TotpEnrollment = { secret: string; otpauthUri: string };
