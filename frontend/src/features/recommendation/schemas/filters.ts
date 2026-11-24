import { graphicsPresets, requirementPlatforms, type GraphicsPreset, type RequirementPlatform } from "../types/recommendation";

/**
 * İstek doğrulaması: backend'in kabul ettiği aralıklar burada tekrar uygulanır ki geçersiz istek ağa çıkmasın
 * (generation kotası dakikada 10'dur, boşa harcanmamalı). Sunucu yine kendi doğrulamasını yapar.
 */
export const CODE_PATTERN = /^[a-z0-9-]{1,64}$/;
export const PLATFORM_PATTERN = /^[a-z0-9-]{1,32}$/;
export const RESOLUTION_PATTERN = /^[1-9]\d{2,4}x[1-9]\d{2,4}$/;
export const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export const SIZE_LIMITS = {
  games: { min: 1, max: 50, fallback: 20 },
  runs: { min: 1, max: 20, fallback: 10 },
  people: { min: 1, max: 20, fallback: 12 },
  events: { min: 1, max: 20, fallback: 12 },
  compatible: { min: 1, max: 20, fallback: 10 }
} as const;

export const GAME_SIZE_OPTIONS = [6, 12, 20, 30, 50] as const;
export const COMPATIBLE_SIZE_OPTIONS = [5, 10, 20] as const;
export const RESOLUTION_OPTIONS = ["1280x720", "1920x1080", "2560x1440", "3840x2160"] as const;

export function clampSize(kind: keyof typeof SIZE_LIMITS, value: number | undefined): number {
  const limit = SIZE_LIMITS[kind];
  if (value === undefined || !Number.isInteger(value)) return limit.fallback;
  return Math.min(limit.max, Math.max(limit.min, value));
}

/** Boş ise `undefined` (alan hiç gönderilmez); desene uymayan değer de gönderilmez. */
export function optionalCode(value: string | null | undefined, pattern: RegExp = CODE_PATTERN): string | undefined {
  const trimmed = value?.trim();
  return trimmed && pattern.test(trimmed) ? trimmed : undefined;
}

export function isUuid(value: string | null | undefined): value is string {
  return typeof value === "string" && UUID_PATTERN.test(value);
}

export function isRequirementPlatform(value: string): value is RequirementPlatform {
  return (requirementPlatforms as readonly string[]).includes(value);
}

export function isGraphicsPreset(value: string): value is GraphicsPreset {
  return (graphicsPresets as readonly string[]).includes(value);
}

export function isResolution(value: string): boolean {
  return RESOLUTION_PATTERN.test(value);
}

/** Etkinlik servisi tür/platform kodlarını `[a-z0-9]` biçimine indirger; katalog kodunu eşlemek için aynı normalizasyon. */
export function normalizeCode(value: string): string {
  return value.toLowerCase().replace(/[^a-z0-9]/g, "");
}
