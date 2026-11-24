import {
  PLATFORMS,
  WHEN_PRESETS,
  type Listing,
  type ListingFilters,
  type ListingInput,
  type ListingUpdate,
  type Platform,
  type WhenPreset
} from "../types/lfp.types";

/**
 * İlan formu (LFP-01..09, LFP-12) — sunucu kurallarının istemci aynası (`docs/api.md` → "İlan oluştur"). Nihai karar sunucudadır.
 * Uzunluklar karakter (code point) olarak sayılır; baş/son boşluk atılır; kontrol karakteri reddedilir (açıklamada satır sonu
 * ve sekme serbest).
 */
export const LIMITS = {
  skillLevel: 50,
  role: 30,
  roles: 5,
  description: 1000,
  playersMin: 1,
  playersMax: 50,
  /** Oyun aralığı en çok 24 saat. */
  playSpanHours: 24
} as const;

/** İlan süresi seçenekleri (saat, 1–168). Varsayılan 24. */
export const DURATION_OPTIONS = [3, 6, 12, 24, 48, 72, 168] as const;
export const DEFAULT_DURATION = 24;

export type ListingFormState = {
  gameId: string;
  gameTitle: string;
  gameCoverUrl: string | null;
  platform: Platform | "";
  skillLevel: string;
  roles: string[];
  playersNeeded: number;
  /** `datetime-local` değeri (yerel saat, `YYYY-MM-DDTHH:mm`); boş = belirtilmedi. */
  playTimeStart: string;
  playTimeEnd: string;
  micRequired: boolean;
  description: string;
  durationHours: number;
};

export type FormField = "gameId" | "platform" | "skillLevel" | "roles" | "playersNeeded" | "playTimeStart" | "playTimeEnd" | "description" | "durationHours";

export type FormIssues = Partial<Record<FormField, string>>;

export function emptyForm(): ListingFormState {
  return {
    gameId: "",
    gameTitle: "",
    gameCoverUrl: null,
    platform: "",
    skillLevel: "",
    roles: [],
    playersNeeded: 1,
    playTimeStart: "",
    playTimeEnd: "",
    micRequired: false,
    description: "",
    durationHours: DEFAULT_DURATION
  };
}

const CONTROL = /[\u0000-\u001F\u007F-\u009F​‌‎‏‪-‮⁠-⁤﻿]/;
const DESCRIPTION_CONTROL = /[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F-\u009F​‌‎‏‪-‮⁠-⁤﻿]/;

export const length = (value: string) => [...value].length;

/** `datetime-local` (yerel) → ISO-8601 UTC. Geçersizse `null`. */
export function localToIso(value: string): string | null {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

/** ISO-8601 → `datetime-local` değeri (kullanıcının yerel saati). */
export function isoToLocal(value: string | null): string {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const pad = (number: number) => String(number).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** Rol ekleme: boş, uzun, kontrol karakterli ya da (büyük/küçük harf farkıyla) tekrarlanan rol eklenmez. */
export function addRole(roles: readonly string[], raw: string): { roles: string[]; issue?: "empty" | "tooLong" | "invalid" | "duplicate" | "tooMany" } {
  const role = raw.trim();
  if (!role) return { roles: [...roles], issue: "empty" };
  if (length(role) > LIMITS.role) return { roles: [...roles], issue: "tooLong" };
  if (CONTROL.test(role)) return { roles: [...roles], issue: "invalid" };
  if (roles.some((item) => item.toLocaleLowerCase() === role.toLocaleLowerCase())) return { roles: [...roles], issue: "duplicate" };
  if (roles.length >= LIMITS.roles) return { roles: [...roles], issue: "tooMany" };
  return { roles: [...roles, role] };
}

/**
 * Formu doğrular. `mode = "edit"` iken oyun ve süre değişmez; başlangıç değişmediyse "geçmişte olamaz" kuralı uygulanmaz
 * (sunucu kuralıyla aynı). Dönüş: alan → çeviri anahtarı (`Lfp.form.issue.*`).
 */
export function validateForm(
  form: ListingFormState,
  options: { mode: "create" | "edit"; now?: Date; expiresAt?: string; originalStart?: string | null } = { mode: "create" }
): FormIssues {
  const issues: FormIssues = {};
  const now = options.now ?? new Date();
  if (options.mode === "create" && !form.gameId) issues.gameId = "gameRequired";
  if (!form.platform) issues.platform = "platformRequired";
  const skill = form.skillLevel.trim();
  if (length(skill) > LIMITS.skillLevel) issues.skillLevel = "skillTooLong";
  else if (skill && CONTROL.test(skill)) issues.skillLevel = "invalidCharacters";
  if (form.roles.length === 0) issues.roles = "rolesRequired";
  if (!Number.isInteger(form.playersNeeded) || form.playersNeeded < LIMITS.playersMin || form.playersNeeded > LIMITS.playersMax) {
    issues.playersNeeded = "playersRange";
  }
  const description = form.description.trim();
  if (!description) issues.description = "descriptionRequired";
  else if (length(description) > LIMITS.description) issues.description = "descriptionTooLong";
  else if (DESCRIPTION_CONTROL.test(description)) issues.description = "invalidCharacters";

  const start = localToIso(form.playTimeStart);
  const end = localToIso(form.playTimeEnd);
  if (form.playTimeEnd && !form.playTimeStart) issues.playTimeEnd = "endNeedsStart";
  if (start) {
    const startMs = Date.parse(start);
    const unchanged = options.mode === "edit" && options.originalStart && Date.parse(options.originalStart) === startMs;
    if (!unchanged && startMs < now.getTime() - 5 * 60_000) issues.playTimeStart = "startInPast";
    const expiry = options.expiresAt ? Date.parse(options.expiresAt) : now.getTime() + form.durationHours * 3_600_000;
    if (!issues.playTimeStart && startMs >= expiry) issues.playTimeStart = "startAfterExpiry";
    if (end) {
      const endMs = Date.parse(end);
      if (endMs <= startMs) issues.playTimeEnd = "endBeforeStart";
      else if (endMs - startMs > LIMITS.playSpanHours * 3_600_000) issues.playTimeEnd = "spanTooLong";
    }
  }
  return issues;
}

function common(form: ListingFormState) {
  const skill = form.skillLevel.trim();
  const start = localToIso(form.playTimeStart);
  const end = start ? localToIso(form.playTimeEnd) : null;
  return {
    platform: form.platform as Platform,
    ...(skill ? { skillLevel: skill } : {}),
    roles: form.roles,
    playersNeeded: form.playersNeeded,
    ...(start ? { playTimeStart: start } : {}),
    ...(end ? { playTimeEnd: end } : {}),
    micRequired: form.micRequired,
    description: form.description.trim()
  };
}

export function toCreateInput(form: ListingFormState): ListingInput {
  return { gameId: form.gameId, ...common(form), durationHours: form.durationHours };
}

export function toUpdateInput(form: ListingFormState, version: number): ListingUpdate {
  return { ...common(form), version };
}

export function formFromListing(listing: Listing, coverUrl: string | null): ListingFormState {
  return {
    gameId: listing.gameId,
    gameTitle: listing.gameTitle,
    gameCoverUrl: coverUrl,
    platform: listing.platform,
    skillLevel: listing.skillLevel ?? "",
    roles: [...listing.roles],
    playersNeeded: listing.playersNeeded,
    playTimeStart: isoToLocal(listing.playTimeStart),
    playTimeEnd: isoToLocal(listing.playTimeEnd),
    micRequired: listing.micRequired,
    description: listing.description,
    durationHours: DEFAULT_DURATION
  };
}

// ---- Süzgeçler ↔ adres çubuğu (web-rules §7.1: paylaşılabilir, geri/ileri çalışır) ----

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const text = (value: string | null, max: number) => {
  const trimmed = value?.trim() ?? "";
  return trimmed && length(trimmed) <= max && !CONTROL.test(trimmed) ? trimmed : undefined;
};

/** Adres parametrelerinden süzgeç okur; geçersiz değer sessizce atlanır (sunucuya bozuk süzgeç gitmez). */
export function filtersFromParams(params: URLSearchParams): ListingFilters {
  const platform = params.get("platform");
  const when = params.get("when");
  const mic = params.get("mic");
  const gameId = params.get("game");
  return {
    q: text(params.get("q"), 100),
    gameId: gameId && UUID.test(gameId) ? gameId : undefined,
    platform: (PLATFORMS as readonly string[]).includes(platform ?? "") ? (platform as Platform) : undefined,
    skillLevel: text(params.get("skill"), LIMITS.skillLevel),
    role: text(params.get("role"), LIMITS.role),
    micRequired: mic === "required" ? true : mic === "optional" ? false : undefined,
    when: (WHEN_PRESETS as readonly string[]).includes(when ?? "") ? (when as WhenPreset) : undefined
  };
}

export function filtersToQuery(filters: ListingFilters): Record<string, string> {
  const query: Record<string, string> = {};
  if (filters.q) query.q = filters.q;
  if (filters.gameId) query.game = filters.gameId;
  if (filters.platform) query.platform = filters.platform;
  if (filters.skillLevel) query.skill = filters.skillLevel;
  if (filters.role) query.role = filters.role;
  if (filters.micRequired !== undefined) query.mic = filters.micRequired ? "required" : "optional";
  if (filters.when) query.when = filters.when;
  return query;
}

export function activeFilterCount(filters: ListingFilters): number {
  return Object.values(filtersToQuery(filters)).length - (filters.q ? 1 : 0);
}
