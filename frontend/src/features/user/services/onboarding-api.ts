import type { PlayerType } from "../types/profile.types";
import { userRequest } from "./user-http";

const ONBOARDING_PATH = "/api/v1/user/me/onboarding";

/** Kurulumun eksik adımları (sunucu kodları). */
export type OnboardingStep = "FAVORITE_GENRES" | "FAVORITE_PLATFORMS";

/** `GET /api/v1/user/me/onboarding`: ilk kurulumun durumu. */
export type OnboardingState = {
  completed: boolean;
  completedAt: string | null;
  missing: OnboardingStep[];
  /** Tamamlarken sunucuya aynen geri gönderilir (profil sürümü). */
  version: number;
};

export type OnboardingChoices = {
  version: number;
  playerTypes: PlayerType[];
  favoriteGenres: string[];
  favoritePlatforms: string[];
};

export function toOnboardingState(body: unknown): OnboardingState {
  const r = typeof body === "object" && body !== null ? (body as Record<string, unknown>) : {};
  const missing = Array.isArray(r.missing)
    ? r.missing.filter((item): item is OnboardingStep => item === "FAVORITE_GENRES" || item === "FAVORITE_PLATFORMS")
    : [];
  return {
    // Bilinmiyorsa "tamamlandı" varsayılmaz: yalnız sunucu açıkça `true` derse tamamdır.
    completed: r.completed === true,
    completedAt:
      typeof r.completedAt === "string" && !Number.isNaN(Date.parse(r.completedAt)) ? r.completedAt : null,
    missing,
    version: typeof r.version === "number" && Number.isInteger(r.version) && r.version >= 0 ? r.version : 0
  };
}

export async function getOnboarding(signal?: AbortSignal): Promise<OnboardingState> {
  return toOnboardingState(await userRequest<unknown>(ONBOARDING_PATH, { signal }));
}

/** Seçimleri profile yazar ve kurulumu tamamlar (idempotent). Tür ve platform en az birer tane olmalıdır. */
export async function completeOnboarding(choices: OnboardingChoices, signal?: AbortSignal): Promise<OnboardingState> {
  return toOnboardingState(
    await userRequest<unknown>(`${ONBOARDING_PATH}/complete`, { method: "POST", json: choices, signal })
  );
}
