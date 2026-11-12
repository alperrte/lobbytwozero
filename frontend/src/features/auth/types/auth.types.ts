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
  | "ACCOUNT_LINK_REQUIRED"
  | "ACCOUNT_EMAIL_REQUIRED"
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
  | "DELETION_NOT_PENDING"
  | "DELETION_CANCEL_WINDOW_CLOSED"
  | "INVALID_TWO_FACTOR_CODE"
  | "DELETION_REQUIRES_SUPPORT"
  | "DELETION_REQUIRES_SECOND_FACTOR"
  | "DELETION_SUPPORT_COOLDOWN"
  | "DELETION_SELF_SERVICE_AVAILABLE"
  | "DELETION_SUPPORT_REQUEST_NOT_FOUND"
  | "DELETION_SUPPORT_ALREADY_DECIDED"
  | "TARGET_NOT_ACTIVE"
  | "REAUTHENTICATION_REQUIRED"
  | "CURRENT_PASSWORD_INVALID"
  | "TWO_FACTOR_ALREADY_ENABLED"
  | "TWO_FACTOR_NOT_PENDING"
  | "TWO_FACTOR_NOT_ENABLED"
  | "TWO_FACTOR_REQUIRED_FOR_ROLE"
  | "PASSWORD_REQUIRED"
  | "PASSWORD_ALREADY_SET"
  | "PASSWORD_SET_INVALID"
  | "EMAIL_CHANGE_INVALID"
  | "EMAIL_LOCKED_BY_PROVIDER"
  | "EXTERNAL_IDENTITY_ALREADY_LINKED"
  | "AGE_NOT_ELIGIBLE"
  | "AGE_VERIFICATION_BLOCKED"
  | "INVALID_BIRTH_DATE"
  | "STEP_UP_REQUIRED"
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
  "ACCOUNT_LINK_REQUIRED",
  "ACCOUNT_EMAIL_REQUIRED",
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
  "DELETION_NOT_PENDING",
  "DELETION_CANCEL_WINDOW_CLOSED",
  "INVALID_TWO_FACTOR_CODE",
  "DELETION_REQUIRES_SUPPORT",
  "DELETION_REQUIRES_SECOND_FACTOR",
  "DELETION_SUPPORT_COOLDOWN",
  "DELETION_SELF_SERVICE_AVAILABLE",
  "DELETION_SUPPORT_REQUEST_NOT_FOUND",
  "DELETION_SUPPORT_ALREADY_DECIDED",
  "TARGET_NOT_ACTIVE",
  "REAUTHENTICATION_REQUIRED",
  "CURRENT_PASSWORD_INVALID",
  "TWO_FACTOR_ALREADY_ENABLED",
  "TWO_FACTOR_NOT_PENDING",
  "TWO_FACTOR_NOT_ENABLED",
  "TWO_FACTOR_REQUIRED_FOR_ROLE",
  "PASSWORD_REQUIRED",
  "PASSWORD_ALREADY_SET",
  "PASSWORD_SET_INVALID",
  "EMAIL_CHANGE_INVALID",
  "EMAIL_LOCKED_BY_PROVIDER",
  "AGE_NOT_ELIGIBLE",
  "AGE_VERIFICATION_BLOCKED",
  "INVALID_BIRTH_DATE",
  "STEP_UP_REQUIRED",
  "EXTERNAL_IDENTITY_ALREADY_LINKED",
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
  /** Hesap türü (A: normal, B: normal + 2FA/sağlayıcı, C: doğrudan Google/Steam kaydı). Eski Auth'ta yoktur. */
  accountTier?: "A" | "B" | "C";
  trusted?: boolean;
  passwordSet?: boolean;
  providers?: string[];
  totpEnabled?: boolean;
  registrationMethod?: string;
  /** Yaş doğrulaması (18+) sonucu; doğum tarihi/yaş dönmez. Eski Auth'ta yoktur (= doğrulanmadı). */
  adultVerified?: boolean;
};

/** B/C türünün yöneticiye açtığı silme talebi (`status`: PENDING, APPROVED, REJECTED; bilinmeyen değer olduğu gibi gelir). */
export type DeletionSupportRequest = {
  id: string;
  status: string;
  createdAt: string;
  decidedAt: string | null;
};

/** Yönetici görünümü: silme talebi (hesap kimliği ve kullanıcının notu dahil; e-posta/kullanıcı adı yoktur). */
export type AdminDeletionRequest = {
  id: string;
  accountId: string;
  status: string;
  message: string | null;
  createdAt: string;
  decidedAt: string | null;
  decidedBy: string | null;
  decisionNote: string | null;
};

/** Yönetici hesap görünümü (`GET /admin/accounts/{id}`): e-posta yoktur, kullanıcı adı vardır. */
export type AdminAccount = {
  id: string;
  username: string;
  status: string;
  roles: string[];
  twoFactorEnabled: boolean;
  createdAt: string;
  suspendedUntil: string | null;
};

/** Yönetici hesap işlemlerinde Auth'un bildirdiği durumlar (`PENDING_VERIFICATION`/silme durumları buradan yönetilmez). */
export type AdminTargetStatus = "ACTIVE" | "SUSPENDED" | "BANNED" | "DISABLED";

/** Tek kullanımlık MFA challenge'ı (bellekte tutulur; URL'ye, depoya ya da günlüğe yazılmaz). */
export type MfaChallenge = { kind: "verify" | "enroll"; id: string };

/** Kurulum başlangıcı: TOTP gizli anahtarı yalnız bu yanıtta görünür. */
export type TotpEnrollment = { secret: string; otpauthUri: string };
