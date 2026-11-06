import { ApiHttpError } from "@/shared/lib/api/client";
import type { AuthMe, LoginResult } from "../types/auth.types";
import { authRequest } from "./auth-http";

/** Giriş uçlarının arayüzü; hook bunu enjekte alır (test için sahte adaptör verilebilir). */
export type LoginApi = {
  login(identifier: string, password: string, signal?: AbortSignal): Promise<LoginResult>;
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
