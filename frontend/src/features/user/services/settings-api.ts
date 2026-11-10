import {
  guestAccesses,
  locales,
  themes,
  visibilities,
  type Consents,
  type GuestAccess,
  type Preferences,
  type PrivacySettings,
  type SettingsLocale,
  type Theme,
  type Visibility
} from "../types/settings.types";
import { userRequest } from "./user-http";

const PRIVACY_PATH = "/api/v1/user/me/privacy";
const PREFERENCES_PATH = "/api/v1/user/me/preferences";
const CONSENTS_PATH = "/api/v1/user/me/consents";

function record(body: unknown): Record<string, unknown> {
  return typeof body === "object" && body !== null ? (body as Record<string, unknown>) : {};
}

function pick<T extends string>(allowed: readonly T[], value: unknown, fallback: T): T {
  return typeof value === "string" && (allowed as readonly string[]).includes(value) ? (value as T) : fallback;
}

function version(value: unknown): number {
  return typeof value === "number" && Number.isInteger(value) && value >= 0 ? value : 0;
}

/**
 * Gövdeyi savunmacı okur. Tanınmayan görünürlük değeri en kısıtlayıcı seçeneğe (`PRIVATE`/`BASIC`) çekilir:
 * bilinmeyen bir değeri daha açık göstermek kullanıcıya yanlış güven verir.
 */
export function toPrivacySettings(body: unknown): PrivacySettings {
  const r = record(body);
  return {
    friendsList: pick<Visibility>(visibilities, r.friendsList, "PRIVATE"),
    library: pick<Visibility>(visibilities, r.library, "PRIVATE"),
    hardware: pick<Visibility>(visibilities, r.hardware, "PRIVATE"),
    performance: pick<Visibility>(visibilities, r.performance, "PRIVATE"),
    guestProfileAccess: pick<GuestAccess>(guestAccesses, r.guestProfileAccess, "BASIC"),
    version: version(r.version)
  };
}

export function toPreferences(body: unknown): Preferences {
  const r = record(body);
  return {
    locale: pick<SettingsLocale>(locales, r.locale, "tr"),
    theme: pick<Theme>(themes, r.theme, "SYSTEM"),
    reducedMotion: r.reducedMotion === true,
    compactLayout: r.compactLayout === true,
    version: version(r.version)
  };
}

/** Rıza yalnız açıkça `true` ise verilmiş sayılır; eksik ya da bozuk alan "rıza yok" demektir. */
export function toConsents(body: unknown): Consents {
  const r = record(body);
  const changedAt = r.recommendationChangedAt;
  return {
    recommendation: r.recommendation === true,
    recommendationChangedAt:
      typeof changedAt === "string" && !Number.isNaN(Date.parse(changedAt)) ? changedAt : null,
    version: version(r.version)
  };
}

export async function getPrivacy(signal?: AbortSignal): Promise<PrivacySettings> {
  return toPrivacySettings(await userRequest<unknown>(PRIVACY_PATH, { signal }));
}

/** Gizlilik ayarlarının tamamını yazar (`version` son görülen sürüm olmalı; uyuşmazsa 409). */
export async function savePrivacy(settings: PrivacySettings, signal?: AbortSignal): Promise<PrivacySettings> {
  return toPrivacySettings(await userRequest<unknown>(PRIVACY_PATH, { method: "PUT", json: settings, signal }));
}

export async function getPreferences(signal?: AbortSignal): Promise<Preferences> {
  return toPreferences(await userRequest<unknown>(PREFERENCES_PATH, { signal }));
}

export async function savePreferences(preferences: Preferences, signal?: AbortSignal): Promise<Preferences> {
  return toPreferences(
    await userRequest<unknown>(PREFERENCES_PATH, { method: "PUT", json: preferences, signal })
  );
}

export async function getConsents(signal?: AbortSignal): Promise<Consents> {
  return toConsents(await userRequest<unknown>(CONSENTS_PATH, { signal }));
}

/** Rızaların tamamını yazar; yalnız `version` ve `recommendation` gönderilir. */
export async function saveConsents(consents: Consents, signal?: AbortSignal): Promise<Consents> {
  return toConsents(
    await userRequest<unknown>(CONSENTS_PATH, {
      method: "PUT",
      json: { version: consents.version, recommendation: consents.recommendation },
      signal
    })
  );
}
