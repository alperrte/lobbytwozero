import { ApiHttpError } from "@/shared/lib/api/client";
import type { PlayerCard } from "../types/activity.types";
import type { PublicDetails, PublicProfile, PublicSteam } from "../types/players.types";
import { toPlayerCards, toPlayerTypes, toProfileImage, toProfileStats, toStringList } from "./profile-api";
import { userRequest } from "./user-http";

const USER_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
/** Sunucudaki kullanıcı adı deseni; uymayan değer için istek atılmaz (sunucu da 404 verir). */
const USERNAME = /^[A-Za-z0-9_]{3,20}$/;
/** Steam profil adresi yalnız bu biçimde kabul edilir: https, steamcommunity.com, 17 haneli kimlik. */
const STEAM_URL = /^https:\/\/steamcommunity\.com\/profiles\/\d{17}$/;

export const MIN_SEARCH_LENGTH = 2;
export const MAX_SEARCH_LENGTH = 32;

function record(value: unknown): Record<string, unknown> {
  return typeof value === "object" && value !== null ? (value as Record<string, unknown>) : {};
}

function text(value: unknown): string | null {
  return typeof value === "string" && value.trim() !== "" ? value : null;
}

/** Steam bağlantısını doğrular; şüpheli adres (başka alan adı, http, kimlik uyuşmazlığı) yok sayılır. */
function toSteam(value: unknown): PublicSteam | null {
  const r = record(value);
  const steamId = typeof r.steamId === "string" && /^\d{17}$/.test(r.steamId) ? r.steamId : null;
  const url = typeof r.profileUrl === "string" && STEAM_URL.test(r.profileUrl) ? r.profileUrl : null;
  return steamId && url && url.endsWith(`/${steamId}`) ? { steamId, profileUrl: url } : null;
}

function toDetails(value: unknown): PublicDetails | null {
  if (typeof value !== "object" || value === null) {
    return null;
  }
  const r = record(value);
  return {
    bio: text(r.bio),
    country: text(r.country),
    playerTypes: toPlayerTypes(r.playerTypes),
    favoriteGenres: toStringList(r.favoriteGenres),
    favoritePlatforms: toStringList(r.favoritePlatforms),
    steam: toSteam(r.steam)
  };
}

/** Gövdeyi savunmacı okur. Bilinmeyen alan yok sayılır; görünürlük bayrakları yalnız açıkça `true` ise açıktır. */
export function toPublicProfile(body: unknown): PublicProfile {
  const r = record(body);
  const visible = record(r.visible);
  return {
    userId: typeof r.userId === "string" ? r.userId : "",
    username: text(r.username),
    displayName: text(r.displayName),
    avatar: toProfileImage(r.avatar),
    cover: toProfileImage(r.cover),
    details: toDetails(r.details),
    visible: {
      friendsList: visible.friendsList === true,
      library: visible.library === true,
      hardware: visible.hardware === true,
      performance: visible.performance === true
    },
    summary: toProfileStats(body)
  };
}

/**
 * Bir oyuncunun açık profili. Anahtar UUID ise kimlikle, değilse kullanıcı adıyla sorulur. Geçersiz anahtar için istek atılmaz
 * ve bulunamadı gibi davranılır: sunucu da var olmayan, gizli ve engelli profilleri aynı 404 ile yanıtlar.
 */
export async function getPublicProfile(key: string, signal?: AbortSignal): Promise<PublicProfile> {
  const value = key.trim();
  if (USER_ID.test(value)) {
    return toPublicProfile(await userRequest<unknown>(`/api/v1/user/profiles/${value.toLowerCase()}`, { signal }));
  }
  if (USERNAME.test(value)) {
    return toPublicProfile(await userRequest<unknown>(`/api/v1/user/profiles/by-username/${value}`, { signal }));
  }
  throw new ApiHttpError(404, { code: "PROFILE_NOT_FOUND" });
}

/** Aramadan önce metni sunucu kuralına göre kırpar; çok kısa ya da çok uzunsa `null`. */
export function normalizeSearch(query: string): string | null {
  const value = query.trim().replace(/\s+/g, " ");
  const length = Array.from(value).length;
  return length >= MIN_SEARCH_LENGTH && length <= MAX_SEARCH_LENGTH ? value : null;
}

/** Oyuncu arama (yalnız oturumlu): kullanıcı adı ya da görünen ad önekiyle en çok 20 kart. */
export async function searchPlayers(query: string, signal?: AbortSignal): Promise<PlayerCard[]> {
  const value = normalizeSearch(query);
  if (value === null) {
    throw new ApiHttpError(400, { code: "INVALID_SEARCH_QUERY" });
  }
  return toPlayerCards(await userRequest<unknown>(`/api/v1/user/search?q=${encodeURIComponent(value)}`, { signal }));
}
