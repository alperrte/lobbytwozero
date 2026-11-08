import { apiUrl } from "@/shared/lib/api/client";
import {
  playerTypes,
  type ImageKind,
  type MyProfile,
  type MyProfileUpdate,
  type CountSection,
  type PlayerType,
  type ProfileImage,
  type ProfileStats,
  type SectionStatus,
  type SteamLink
} from "../types/profile.types";
import { userRequest } from "./user-http";

const MEDIA_PREFIX = "/api/v1/user/media/";

function text(value: unknown): string | null {
  return typeof value === "string" && value.trim() !== "" ? value : null;
}

function mediaPath(value: unknown): `/${string}` | null {
  return typeof value === "string" && value.startsWith(MEDIA_PREFIX) && !value.includes("..")
    ? (value as `/${string}`)
    : null;
}

/** Yalnız User medya yolunu kabul eder; başka yol ya da dış adres yok sayılır. */
function image(value: unknown): ProfileImage | null {
  const record = typeof value === "object" && value !== null ? (value as Record<string, unknown>) : {};
  const small = mediaPath(record.small);
  const large = mediaPath(record.large) ?? small;
  return small && large ? { small: apiUrl(small), large: apiUrl(large) } : null;
}

function strings(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string") : [];
}

/** Gövdeyi savunmacı okur; bilinmeyen alanlar yok sayılır. */
export function toMyProfile(body: unknown): MyProfile {
  const r = typeof body === "object" && body !== null ? (body as Record<string, unknown>) : {};
  return {
    username: text(r.username),
    displayName: text(r.displayName),
    bio: text(r.bio),
    country: text(r.country),
    playerTypes: strings(r.playerTypes).filter((item): item is PlayerType =>
      (playerTypes as readonly string[]).includes(item)
    ),
    favoriteGenres: strings(r.favoriteGenres),
    favoritePlatforms: strings(r.favoritePlatforms),
    avatar: image(r.avatar),
    cover: image(r.cover),
    version: typeof r.version === "number" ? r.version : 0
  };
}

export async function getMyProfile(signal?: AbortSignal): Promise<MyProfile> {
  return toMyProfile(await userRequest<unknown>("/api/v1/user/me", { signal }));
}

export async function updateMyProfile(update: MyProfileUpdate, signal?: AbortSignal): Promise<MyProfile> {
  return toMyProfile(
    await userRequest<unknown>("/api/v1/user/me/profile", { method: "PATCH", json: update, signal })
  );
}

export async function uploadMyImage(kind: ImageKind, file: File, signal?: AbortSignal): Promise<MyProfile> {
  return toMyProfile(
    await userRequest<unknown>(`/api/v1/user/me/${kind}`, {
      method: "PUT",
      raw: { data: file, type: file.type },
      signal
    })
  );
}

export async function removeMyImage(kind: ImageKind, signal?: AbortSignal): Promise<void> {
  await userRequest<void>(`/api/v1/user/me/${kind}`, { method: "DELETE", signal });
}

const USER_ID = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;

function section(value: unknown, countKey: string): CountSection {
  const r = typeof value === "object" && value !== null ? (value as Record<string, unknown>) : {};
  const status: SectionStatus =
    r.status === "AVAILABLE" || r.status === "HIDDEN" ? r.status : "UNAVAILABLE";
  const count = r[countKey];
  return { status, count: status === "AVAILABLE" && typeof count === "number" && count >= 0 ? count : null };
}

/** Yanıttaki `summary` bloğunu okur; eksik ya da tanınmayan bölüm "henüz yok" sayılır. */
export function toProfileStats(body: unknown): ProfileStats {
  const root = typeof body === "object" && body !== null ? (body as Record<string, unknown>) : {};
  const summary =
    typeof root.summary === "object" && root.summary !== null ? (root.summary as Record<string, unknown>) : {};
  return {
    friends: section(summary.friends, "count"),
    library: section(summary.library, "collection"),
    reviews: section(summary.reviews, "count")
  };
}

/** Kendi profilinin sayı özeti (`GET /profiles/{userId}`). Kimlik UUID değilse istek atılmaz. */
export async function getProfileStats(userId: string, signal?: AbortSignal): Promise<ProfileStats> {
  if (!USER_ID.test(userId)) {
    throw new TypeError("userId UUID olmalı");
  }
  return toProfileStats(await userRequest<unknown>(`/api/v1/user/profiles/${userId}`, { signal }));
}

/** Bağlı hesaplar listesinden yalnız Steam durumunu çıkarır. */
export async function getSteamLink(signal?: AbortSignal): Promise<SteamLink> {
  const body = await userRequest<unknown>("/api/v1/user/me/linked-accounts", { signal });
  const accounts =
    typeof body === "object" && body !== null && Array.isArray((body as { accounts?: unknown }).accounts)
      ? ((body as { accounts: unknown[] }).accounts as Record<string, unknown>[])
      : [];
  const steam = accounts.find((account) => account?.provider === "STEAM");
  return { linked: steam?.linked === true, showOnProfile: steam?.showOnProfile === true };
}
