import { ApiHttpError } from "@/shared/lib/api/client";
import type { AuthMe, LoginResult } from "../types/auth.types";
import { authRequest } from "./auth-http";

/** Giriş uçlarının arayüzü; hook bunu enjekte alır (test için sahte adaptör verilebilir). */
export type LoginApi = {
  login(identifier: string, password: string, signal?: AbortSignal): Promise<LoginResult>;
};

/** Kayıt ve e-posta doğrulama uçlarının arayüzü; hook'lar bunu enjekte alır (test için sahte adaptör verilebilir). */
export type RegistrationApi = {
  register(email: string, username: string, password: string, signal?: AbortSignal): Promise<void>;
  confirmEmail(email: string, code: string, password: string, signal?: AbortSignal): Promise<void>;
  resendEmailCode(email: string, signal?: AbortSignal): Promise<void>;
};

/** Şifremi unuttum ve sıfırlama uçlarının arayüzü (test için sahte adaptör verilebilir). */
export type PasswordResetApi = {
  forgotPassword(email: string, signal?: AbortSignal): Promise<void>;
  resetPassword(token: string, newPassword: string, confirmation: string, signal?: AbortSignal): Promise<void>;
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
