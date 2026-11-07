import { ApiHttpError } from "@/shared/lib/api/client";
import type { AuthMe, LoginResult, TotpEnrollment } from "../types/auth.types";
import { authRequest } from "./auth-http";

/** Giriş uçlarının arayüzü; hook bunu enjekte alır (test için sahte adaptör verilebilir). */
export type LoginApi = {
  login(identifier: string, password: string, signal?: AbortSignal): Promise<LoginResult>;
};

/** İki aşamalı doğrulama uçlarının arayüzü: giriş ikinci adımı ve zorunlu TOTP kurulumu. */
export type TwoFactorApi = {
  verifyTwoFactor(challengeId: string, factor: { code: string } | { recoveryCode: string }, signal?: AbortSignal): Promise<void>;
  startTwoFactorEnrollment(challengeId: string, signal?: AbortSignal): Promise<TotpEnrollment>;
  completeTwoFactorEnrollment(challengeId: string, code: string, signal?: AbortSignal): Promise<{ recoveryCodes: string[] }>;
};

/** Kayıt ve e-posta doğrulama uçlarının arayüzü; hook'lar bunu enjekte alır (test için sahte adaptör verilebilir). */
export type RegistrationApi = {
  register(email: string, username: string, password: string, signal?: AbortSignal): Promise<void>;
  confirmEmail(email: string, code: string, password: string, signal?: AbortSignal): Promise<void>;
  resendEmailCode(email: string, signal?: AbortSignal): Promise<void>;
};

/** Steam ile yeni hesap akışının uçları: kayıt tamamlama ve kanıtla (parolasız) e-posta doğrulama. */
export type SteamApi = {
  completeSteamRegistration(registrationToken: string, email: string, username: string, signal?: AbortSignal): Promise<{ verificationProof: string }>;
  confirmEmailWithProof(email: string, code: string, verificationProof: string, signal?: AbortSignal): Promise<void>;
};

/** Şifremi unuttum ve sıfırlama uçlarının arayüzü (test için sahte adaptör verilebilir). */
export type PasswordResetApi = {
  forgotPassword(email: string, signal?: AbortSignal): Promise<void>;
  resetPassword(token: string, newPassword: string, confirmation: string, signal?: AbortSignal): Promise<void>;
};

/** Hesap silme onayı (oturum + CSRF ister): e-postadaki token ve açık onay olarak kullanıcı adı. */
export type AccountDeletionApi = {
  confirmAccountDeletion(token: string, username: string, signal?: AbortSignal): Promise<void>;
};

/** Silme talebinden vazgeçme (public: oturum açmaz, kimlik gövdedeki parolayla doğrulanır; TOTP etkinse kod gerekir). */
export type DeletionCancelApi = {
  cancelAccountDeletion(
    identifier: string,
    password: string,
    factor?: { code: string } | { recoveryCode: string },
    signal?: AbortSignal
  ): Promise<void>;
};

/** Oturum uçlarının arayüzü (yükleme ve çıkış); test için sahte adaptör verilebilir. */
export type SessionApi = {
  getMe(signal?: AbortSignal): Promise<AuthMe>;
  logout(): Promise<void>;
};

export const authApi = {
  /** Yalnız `status === "AUTHENTICATED"` oturum açılmıştır; MFA durumlarında çerez yazılmaz. */
  async login(identifier: string, password: string, signal?: AbortSignal): Promise<LoginResult> {
    const result = await authRequest<unknown>("/api/v1/auth/login", {
      method: "POST",
      body: { identifier, password },
      signal
    });
    return toLoginResult(result);
  },

  /** Giriş ikinci adımı: TOTP kodu ya da tek kullanımlık kurtarma kodu. Başarıda oturum çerezleri yazılır (`200`). */
  async verifyTwoFactor(challengeId: string, factor: { code: string } | { recoveryCode: string }, signal?: AbortSignal): Promise<void> {
    await authRequest<unknown>("/api/v1/auth/login/2fa", { method: "POST", body: { challengeId, ...factor }, signal });
  },

  /** Zorunlu kurulum 1/2: TOTP gizli anahtarı ve `otpauth` adresi (yalnız bu yanıtta; yeniden çağrı anahtarı yeniler). */
  async startTwoFactorEnrollment(challengeId: string, signal?: AbortSignal): Promise<TotpEnrollment> {
    const result = await authRequest<unknown>("/api/v1/auth/login/2fa/enrollment/start", {
      method: "POST",
      body: { challengeId },
      signal
    });
    const record = typeof result === "object" && result !== null ? (result as Record<string, unknown>) : {};
    if (typeof record.secret !== "string" || typeof record.otpauthUri !== "string") {
      throw new ApiHttpError(200, { code: "UNKNOWN" });
    }
    return { secret: record.secret, otpauthUri: record.otpauthUri };
  },

  /** Zorunlu kurulum 2/2: kod doğruysa oturum açılır; kurtarma kodları YALNIZ bu yanıtta döner. */
  async completeTwoFactorEnrollment(challengeId: string, code: string, signal?: AbortSignal): Promise<{ recoveryCodes: string[] }> {
    const result = await authRequest<unknown>("/api/v1/auth/login/2fa/enrollment/complete", {
      method: "POST",
      body: { challengeId, code },
      signal
    });
    const codes = typeof result === "object" && result !== null ? (result as Record<string, unknown>).recoveryCodes : undefined;
    return { recoveryCodes: Array.isArray(codes) ? codes.filter((item): item is string => typeof item === "string") : [] };
  },

  /** Silme onayı: `202`; hesap `DELETION_REQUESTED` olur, tüm oturumlar sunucuda iptal edilir, 14 gün geri alma süresi başlar. */
  async confirmAccountDeletion(token: string, username: string, signal?: AbortSignal): Promise<void> {
    await authRequest<unknown>("/api/v1/auth/account/deletion/confirm", {
      method: "POST",
      body: { token, username },
      signal
    });
  },

  /** Bekleme süresi içinde silme talebinden vazgeçer (`200`): hesap `ACTIVE` olur; oturum AÇILMAZ, kullanıcı normal girişle devam eder. */
  async cancelAccountDeletion(
    identifier: string,
    password: string,
    factor?: { code: string } | { recoveryCode: string },
    signal?: AbortSignal
  ): Promise<void> {
    await authRequest<unknown>("/api/v1/auth/account/deletion/cancel", {
      method: "POST",
      body: { identifier, password, ...factor },
      signal
    });
  },

  /** Kayıt: her durumda `202` (e-posta kayıtlı olsa da aynı yanıt; hesap varlığı sızmaz) ve doğrulama kodu gönderilir. */
  async register(email: string, username: string, password: string, signal?: AbortSignal): Promise<void> {
    await authRequest<unknown>("/api/v1/auth/register", {
      method: "POST",
      body: { email, username, password },
      signal
    });
  },

  /** E-posta doğrulaması: `204`, oturum AÇMAZ (kullanıcı giriş sayfasına yönlendirilir). */
  confirmEmail(email: string, code: string, password: string, signal?: AbortSignal): Promise<void> {
    return authRequest<void>("/api/v1/auth/email-verification/confirm", {
      method: "POST",
      body: { email, code, password },
      signal
    });
  },

  /** Steam ile yeni hesap: kayıt token'ı + e-posta + kullanıcı adı. Yanıttaki tek kullanımlık kanıt doğrulama adımında parola yerine gider. */
  async completeSteamRegistration(
    registrationToken: string,
    email: string,
    username: string,
    signal?: AbortSignal
  ): Promise<{ verificationProof: string }> {
    const result = await authRequest<unknown>("/api/v1/auth/oauth/steam/complete", {
      method: "POST",
      body: { registrationToken, email, username },
      signal
    });
    const proof = typeof result === "object" && result !== null ? (result as Record<string, unknown>).verificationProof : undefined;
    if (typeof proof !== "string" || proof === "") {
      throw new ApiHttpError(200, { code: "UNKNOWN" });
    }
    return { verificationProof: proof };
  },

  /** Steam hesabında e-posta doğrulaması: parola yerine `verificationProof` gönderilir (`204`, oturum AÇMAZ). */
  confirmEmailWithProof(email: string, code: string, verificationProof: string, signal?: AbortSignal): Promise<void> {
    return authRequest<void>("/api/v1/auth/email-verification/confirm", {
      method: "POST",
      body: { email, code, verificationProof },
      signal
    });
  },

  /** Kodu yeniden gönderir: her durumda `202` (hesap varlığı sızmaz); sunucuda 60 sn bekleme ve günlük sınır vardır. */
  async resendEmailCode(email: string, signal?: AbortSignal): Promise<void> {
    await authRequest<unknown>("/api/v1/auth/email-verification/resend", {
      method: "POST",
      body: { email },
      signal
    });
  },

  /** Şifre sıfırlama bağlantısı ister: her durumda `202` (hesap varlığı sızmaz; e-posta yalnız uygun hesaba gider). */
  async forgotPassword(email: string, signal?: AbortSignal): Promise<void> {
    await authRequest<unknown>("/api/v1/auth/password/forgot", { method: "POST", body: { email }, signal });
  },

  /** Yeni parola belirler (`204`); tüm oturumlar iptal edilir, kullanıcı giriş sayfasına yönlendirilir. */
  resetPassword(token: string, newPassword: string, confirmation: string, signal?: AbortSignal): Promise<void> {
    return authRequest<void>("/api/v1/auth/password/reset", {
      method: "POST",
      body: { token, newPassword, newPasswordConfirmation: confirmation },
      signal
    });
  },

  /** Oturum yoklaması. 401 = misafir ya da oturum geçersiz (HTTP katmanı önce yenilemeyi dener). */
  getMe(signal?: AbortSignal): Promise<AuthMe> {
    return authRequest<AuthMe>("/api/v1/auth/me", { signal });
  },

  /** Bu cihazdaki oturumu kapatır (204, idempotent). */
  logout(): Promise<void> {
    return authRequest<void>("/api/v1/auth/session/logout", { method: "POST" });
  }
} as const;

/** Bilinmeyen ya da eksik yanıtı oturum açılmış SAYMAZ. */
export function toLoginResult(value: unknown): LoginResult {
  if (typeof value === "object" && value !== null) {
    const record = value as Record<string, unknown>;
    if (record.status === "AUTHENTICATED" && typeof record.accessTokenExpiresAt === "string") {
      return { status: "AUTHENTICATED", accessTokenExpiresAt: record.accessTokenExpiresAt };
    }
    if (
      (record.status === "MFA_REQUIRED" || record.status === "MFA_ENROLLMENT_REQUIRED") &&
      typeof record.challengeId === "string" &&
      typeof record.challengeExpiresAt === "string"
    ) {
      return {
        status: record.status,
        challengeId: record.challengeId,
        challengeExpiresAt: record.challengeExpiresAt
      };
    }
  }
  throw new ApiHttpError(200, { code: "UNKNOWN" });
}
