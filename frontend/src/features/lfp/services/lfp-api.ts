import { apiRequest } from "@/shared/lib/api/client";
import type {
  ContactResult,
  Listing,
  ListingFilters,
  ListingInput,
  ListingPage,
  ListingStatus,
  ListingUpdate
} from "../types/lfp.types";
import { lfpRequest } from "./lfp-http";

/**
 * lfp-service istemcisi (`docs/api.md` → `## LFP`). Tüm yollar Gateway'in `/api/v1/lfp/**` önekindedir. Sahip kimliği yalnız
 * oturum çerezinden gelir; gövdeye yazılmaz (sunucu bilinmeyen alanı `400` ile reddeder).
 */
const BASE = "/api/v1/lfp/listings";

const id = (value: string) => encodeURIComponent(value);

function withQuery(path: string, query: Record<string, string | number | boolean | undefined>): `/${string}` {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== "") params.set(key, String(value));
  }
  const search = params.toString();
  return `${path}${search ? `?${search}` : ""}` as `/${string}`;
}

/** Zaman ön ayarını kullanıcının yerel gününe göre UTC aralığına çevirir (sunucu `Z` ile ISO ister). */
export function playWindow(preset: ListingFilters["when"], now = new Date()): { playFrom?: string; playTo?: string } {
  if (!preset) return {};
  const start = new Date(now);
  start.setHours(0, 0, 0, 0);
  const days = preset === "week" ? 7 : 1;
  if (preset === "tomorrow") start.setDate(start.getDate() + 1);
  const from = preset === "today" || preset === "week" ? now : start;
  const end = new Date(start);
  end.setDate(end.getDate() + days);
  return { playFrom: from.toISOString(), playTo: new Date(end.getTime() - 1000).toISOString() };
}

export const lfpApi = {
  search(filters: ListingFilters, page: { limit?: number; cursor?: string | null } = {}, signal?: AbortSignal) {
    const { when, ...rest } = filters;
    return lfpRequest<ListingPage>(
      withQuery(BASE, { ...rest, ...playWindow(when), limit: page.limit, cursor: page.cursor ?? undefined }),
      { signal }
    );
  },

  mine(query: { status?: ListingStatus; limit?: number; cursor?: string | null } = {}, signal?: AbortSignal) {
    return lfpRequest<ListingPage>(
      withQuery(`${BASE}/mine`, { status: query.status, limit: query.limit, cursor: query.cursor ?? undefined }),
      { signal }
    );
  },

  get(listingId: string, signal?: AbortSignal) {
    return lfpRequest<Listing>(`${BASE}/${id(listingId)}`, { signal });
  },

  create(input: ListingInput) {
    return lfpRequest<Listing>(BASE, { method: "POST", body: input });
  },

  update(listingId: string, input: ListingUpdate) {
    return lfpRequest<Listing>(`${BASE}/${id(listingId)}`, { method: "PUT", body: input });
  },

  close(listingId: string) {
    return lfpRequest<Listing>(`${BASE}/${id(listingId)}/close`, { method: "POST" });
  },

  contact(listingId: string) {
    return lfpRequest<ContactResult>(`${BASE}/${id(listingId)}/contact`, { method: "POST" });
  }
};

type GameCard = { gameId: string; title: string; coverUrl?: string | null; externalReferences?: { provider: string; externalId: string }[] };

/**
 * İlan kartlarındaki oyun kapakları: game-service herkese açık toplu kart ucu (en çok 50 kimlik, sayfa başına tek istek;
 * web-rules §7.1). Uç çerez kullanmaz; çerez gönderilirse Gateway durum değiştiren istek sayıp CSRF ister.
 */
export function fetchGameCards(gameIds: readonly string[], signal?: AbortSignal): Promise<{ items: GameCard[] }> {
  const ids = [...new Set(gameIds)].slice(0, 50);
  if (ids.length === 0) return Promise.resolve({ items: [] });
  return apiRequest<{ items: GameCard[] }>("/api/v1/game/games/batch", {
    method: "POST",
    credentials: "omit",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ gameIds: ids }),
    signal
  });
}

export type LfpSource = typeof lfpApi;
