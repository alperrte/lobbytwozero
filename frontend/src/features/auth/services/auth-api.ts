import { ApiHttpError } from "@/shared/lib/api/client";
import type { AdminAccount, AdminDeletionRequest, AdminTargetStatus, AuthMe, DeletionSupportRequest, LoginResult, TotpEnrollment } from "../types/auth.types";
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

/** İlk Google/Steam girişinde kullanıcı adı seçerek hesabı tamamlama (sonuç giriş yanıtıyla aynıdır: oturum ya da MFA). */
export type ExternalRegistrationApi = {
  completeExternalRegistration(provider: "google" | "steam", registrationToken: string, username: string, signal?: AbortSignal): Promise<LoginResult>;
};

/** Eski (e-postayla açılmış) Steam hesabının kanıtla, parolasız e-posta doğrulaması. */
export type SteamApi = {
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

function toSupportRequest(value: unknown): DeletionSupportRequest {
  const r = typeof value === "object" && value !== null ? (value as Record<string, unknown>) : {};
  return {
    id: typeof r.id === "string" ? r.id : "",
    status: typeof r.status === "string" ? r.status : "UNKNOWN",
    createdAt: typeof r.createdAt === "string" ? r.createdAt : "",
    decidedAt: typeof r.decidedAt === "string" ? r.decidedAt : null
  };
}

/** Yönetici silme talebi kuyruğu (ROLE_ADMIN; yetki kararı sunucudadır). */
export type AdminDeletionApi = {
  listDeletionRequests(limit?: number, signal?: AbortSignal): Promise<AdminDeletionRequest[]>;
  approveDeletionRequest(requestId: string, message?: string, signal?: AbortSignal): Promise<void>;
  rejectDeletionRequest(requestId: string, message?: string, signal?: AbortSignal): Promise<void>;
};

function toAdminRequest(value: unknown): AdminDeletionRequest {
  const r = typeof value === "object" && value !== null ? (value as Record<string, unknown>) : {};
  const text = (key: string) => (typeof r[key] === "string" ? (r[key] as string) : null);
  return {
    id: text("id") ?? "",
    accountId: text("accountId") ?? "",
    status: text("status") ?? "UNKNOWN",
    message: text("message"),
    createdAt: text("createdAt") ?? "",
    decidedAt: text("decidedAt"),
    decidedBy: text("decidedBy"),
    decisionNote: text("decisionNote")
  };
}

function toAdminAccount(value: unknown): AdminAccount {
  const r = typeof value === "object" && value !== null ? (value as Record<string, unknown>) : {};
  return {
    id: typeof r.id === "string" ? r.id : "",
    username: typeof r.username === "string" ? r.username : "",
    status: typeof r.status === "string" ? r.status : "UNKNOWN",
    roles: Array.isArray(r.roles) ? r.roles.filter((item): item is string => typeof item === "string") : [],
    twoFactorEnabled: r.twoFactorEnabled === true,
    createdAt: typeof r.createdAt === "string" ? r.createdAt : "",
    suspendedUntil: typeof r.suspendedUntil === "string" ? r.suspendedUntil : null
  };
}

export const authApi = {
  /** Tanımlı rol kodları (`ROLE_ADMIN` gerekir). */
  async listAdminRoles(signal?: AbortSignal): Promise<string[]> {
    const result = await authRequest<unknown>("/api/v1/auth/admin/roles", { signal });
    return Array.isArray(result) ? result.filter((item): item is string => typeof item === "string") : [];
  },

  /** Hesap kimliğiyle yönetici görünümü. */
  async getAdminAccount(accountId: string, signal?: AbortSignal): Promise<AdminAccount> {
    return toAdminAccount(await authRequest<unknown>(`/api/v1/auth/admin/accounts/${encodeURIComponent(accountId)}`, { signal }));
  },

  /** Durum değiştirir (`204`); gerekçe zorunludur ve denetim kaydına yazılır. `suspendedUntil` yalnız SUSPENDED için. */
  async changeAdminAccountStatus(
    accountId: string,
    body: { status: AdminTargetStatus; reason: string; suspendedUntil?: string },
    signal?: AbortSignal
  ): Promise<void> {
    await authRequest<unknown>(`/api/v1/auth/admin/accounts/${encodeURIComponent(accountId)}/status`, {
      method: "POST",
      body,
      signal
    });
  },

  /** Rol verir ya da geri alır (`204`); gerekçe zorunludur. */
  async changeAdminAccountRole(
    accountId: string,
    action: "grant" | "revoke",
    body: { role: string; reason: string },
    signal?: AbortSignal
  ): Promise<void> {
    await authRequest<unknown>(`/api/v1/auth/admin/accounts/${encodeURIComponent(accountId)}/roles/${action}`, {
      method: "POST",
      body,
      signal
    });
  },

  /** Bekleyen silme talepleri, en eski önce (`limit` sunucuda sınırlanır). */
  async listDeletionRequests(limit = 50, signal?: AbortSignal): Promise<AdminDeletionRequest[]> {
    const result = await authRequest<unknown>(`/api/v1/auth/admin/account-deletion-requests?limit=${limit}`, { signal });
    return Array.isArray(result) ? result.map(toAdminRequest) : [];
  },

  /** Onay standart silme sürecini başlatır (bekleme süresi, servis görevleri, oturumlar kapanır). */
  async approveDeletionRequest(requestId: string, message?: string, signal?: AbortSignal): Promise<void> {
    await authRequest<unknown>(`/api/v1/auth/admin/account-deletion-requests/${encodeURIComponent(requestId)}/approve`, {
      method: "POST",
      body: message ? { message } : {},
      signal
    });
  },

  /** Ret hesabı değiştirmez; kullanıcı 24 saat yeni talep açamaz. */
  async rejectDeletionRequest(requestId: string, message?: string, signal?: AbortSignal): Promise<void> {
    await authRequest<unknown>(`/api/v1/auth/admin/account-deletion-requests/${encodeURIComponent(requestId)}/reject`, {
      method: "POST",
      body: message ? { message } : {},
      signal
    });
  },

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

  /** TOTP kurulumu başlatır: gizli anahtar yalnız bu yanıtta görünür. */
  async setupTotp(signal?: AbortSignal): Promise<TotpEnrollment> {
    const result = await authRequest<unknown>("/api/v1/auth/2fa/totp/setup", { method: "POST", signal });
    const r = typeof result === "object" && result !== null ? (result as Record<string, unknown>) : {};
    return {
      secret: typeof r.secret === "string" ? r.secret : "",
      otpauthUri: typeof r.otpauthUri === "string" ? r.otpauthUri : ""
    };
  },

  /** Kurulumu koda göre etkinleştirir; kurtarma kodları yalnız bu yanıtta görünür. */
  async activateTotp(code: string, signal?: AbortSignal): Promise<{ recoveryCodes: string[] }> {
    const result = await authRequest<unknown>("/api/v1/auth/2fa/totp/activate", { method: "POST", body: { code }, signal });
    const codes = typeof result === "object" && result !== null ? (result as Record<string, unknown>).recoveryCodes : undefined;
    return { recoveryCodes: Array.isArray(codes) ? codes.filter((item): item is string => typeof item === "string") : [] };
  },

  /** TOTP'yi kapatır (`204`): parolalı hesapta parola + kod ya da kurtarma kodu, parolasızda yalnız kod. */
  async disableTotp(body: { password?: string; code?: string; recoveryCode?: string }, signal?: AbortSignal): Promise<void> {
    await authRequest<unknown>("/api/v1/auth/2fa/totp/disable", { method: "POST", body, signal });
  },

  /** Google/Steam bağlamayı başlatır (yeniden doğrulama ister); dönen adrese tarayıcı gönderilir. */
  async startProviderLink(
    provider: "google" | "steam",
    body: { password: string; code?: string; recoveryCode?: string },
    signal?: AbortSignal
  ): Promise<string> {
    const result = await authRequest<unknown>(`/api/v1/auth/oauth/${provider}/link/start`, { method: "POST", body, signal });
    const url = typeof result === "object" && result !== null ? (result as Record<string, unknown>).redirectUrl : undefined;
    if (typeof url !== "string" || url === "") throw new ApiHttpError(200, { code: "UNKNOWN" });
    return url;
  },

  /** Parolasız hesap için: doğrulanmış e-postaya parola belirleme kodu gönderir (`202`). */
  async requestPasswordSet(factor?: { code: string } | { recoveryCode: string }, signal?: AbortSignal): Promise<void> {
    await authRequest<unknown>("/api/v1/auth/password/set/request", { method: "POST", body: { ...factor }, signal });
  },

  /** Kodla parolayı belirler (`204`); tüm oturumlar kapanır, yeniden giriş gerekir. */
  async setPassword(emailCode: string, newPassword: string, newPasswordConfirmation: string, signal?: AbortSignal): Promise<void> {
    await authRequest<unknown>("/api/v1/auth/password/set", {
      method: "POST",
      body: { emailCode, newPassword, newPasswordConfirmation },
      signal
    });
  },

  /** E-posta değişikliğini başlatır (`202`): kod YENİ adrese gider, adres henüz değişmez. */
  async requestEmailChange(
    body: { newEmail: string; password?: string; code?: string; recoveryCode?: string },
    signal?: AbortSignal
  ): Promise<void> {
    await authRequest<unknown>("/api/v1/auth/email/change", { method: "POST", body, signal });
  },

  /** Yeni adrese gelen kodla değişikliği onaylar (`204`); tüm oturumlar kapanır, yeni adresle yeniden giriş gerekir. */
  async confirmEmailChange(code: string, signal?: AbortSignal): Promise<void> {
    await authRequest<unknown>("/api/v1/auth/email/change/confirm", { method: "POST", body: { code }, signal });
  },

  /** Tüm cihazlardaki oturumları kapatır (`204`), bu cihazdaki dahil. */
  async logoutAll(signal?: AbortSignal): Promise<void> {
    await authRequest<unknown>("/api/v1/auth/session/logout-all", { method: "POST", signal });
  },

  /** A türü: yeniden doğrulama sonrası silme onayı e-postası gönderir (`202`, hesap henüz silinmez). */
  async requestAccountDeletion(
    body: { password?: string } & Partial<{ code: string; recoveryCode: string }>,
    signal?: AbortSignal
  ): Promise<void> {
    await authRequest<unknown>("/api/v1/auth/account/deletion/request", { method: "POST", body, signal });
  },

  /** B/C türü: yöneticiye silme talebi açar (`202`). Parolalı hesapta `password`, TOTP açıksa kod ya da kurtarma kodu gerekir. */
  async requestDeletionSupport(
    body: { message?: string; password?: string; code?: string; recoveryCode?: string },
    signal?: AbortSignal
  ): Promise<DeletionSupportRequest> {
    return toSupportRequest(
      await authRequest<unknown>("/api/v1/auth/account/deletion/support-request", { method: "POST", body, signal })
    );
  },

  /** En son silme talebi; hiç talep yoksa (`204`) `null`. */
  async getDeletionSupportRequest(signal?: AbortSignal): Promise<DeletionSupportRequest | null> {
    const result = await authRequest<unknown>("/api/v1/auth/account/deletion/support-request", { signal });
    return result === undefined || result === null ? null : toSupportRequest(result);
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

  /**
   * İlk Google/Steam girişi: sağlayıcıda doğrulanmış kimliğe bağlı tek kullanımlık kayıt token'ı + seçilen kullanıcı adı.
   * E-posta gönderilmez (Google'dan sunucu alır, Steam e-posta vermez). Başarıda yanıt giriş yanıtıdır: oturum çerezleri
   * yazılır ya da MFA gerekiyorsa challenge döner (MFA atlanmaz).
   */
  async completeExternalRegistration(
    provider: "google" | "steam",
    registrationToken: string,
    username: string,
    signal?: AbortSignal
  ): Promise<LoginResult> {
    const result = await authRequest<unknown>(`/api/v1/auth/oauth/${provider}/complete`, {
      method: "POST",
      body: { registrationToken, username },
      signal
    });
    return toLoginResult(result);
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

  /** Yaş beyanı (18+). Doğum tarihi yalnız bu isteğin gövdesinde gider; saklanmaz, URL'e/loga yazılmaz. */
  async verifyAge(birthDate: string, signal?: AbortSignal): Promise<void> {
    await authRequest<unknown>("/api/v1/auth/account/age-verification", { method: "POST", body: { birthDate }, signal });
  },

  /** Yaş doğrulamasını geri alır (204, idempotent). */
  async revokeAge(signal?: AbortSignal): Promise<void> {
    await authRequest<unknown>("/api/v1/auth/account/age-verification", { method: "DELETE", signal });
  },

  /** Access token'ı yeniler (yeni `adult_verified` iddiası için). */
  async refreshSession(signal?: AbortSignal): Promise<void> {
    await authRequest<unknown>("/api/v1/auth/session/refresh", { method: "POST", signal });
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
