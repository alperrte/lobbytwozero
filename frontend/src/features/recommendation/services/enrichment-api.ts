import { isUuid } from "../schemas/filters";
import type { CatalogGame, CatalogTerm, EventParticipation, GameCard, GameMetadata, ProfileCard, RecommendationConsent } from "../types/recommendation";
import { readJson, writeJson } from "./recommendation-http";

/**
 * Önerileri anlamlı kılan diğer servis uçları (`docs/api.md`): Recommendation yalnız kimlik + sıra döner; ad, kapak, avatar
 * buradan eklenir. `features/game` ve `features/user` açık export vermediği için iç dosyalarına derin import yapılmaz
 * (Review/Social'daki `gameLookup`/`fetchProfileCards` emsali). Her yanıt savunmacı okunur; bozuk kayıt atlanır.
 */
type Json = Record<string, unknown>;
const record = (value: unknown): Json => (typeof value === "object" && value !== null ? (value as Json) : {});
const list = (value: unknown): unknown[] => (Array.isArray(value) ? value : []);
const text = (value: unknown): string | null => (typeof value === "string" ? value : null);

export const GAME_BATCH_LIMIT = 50;
export const PROFILE_BATCH_LIMIT = 50;

function uniqueIds(ids: readonly string[], limit: number): string[] {
  return [...new Set(ids.filter(isUuid))].slice(0, limit);
}

function chunks<T>(values: readonly T[], size: number): T[][] {
  const result: T[][] = [];
  for (let index = 0; index < values.length; index += size) result.push(values.slice(index, index + size));
  return result;
}

/** Public toplu okumalar çerezsiz gider: çerez gönderilirse Gateway CSRF ister ve 403 döner. */
function publicBatch(path: `/${string}`, gameIds: string[], signal?: AbortSignal) {
  return readJson(path, signal, { method: "POST", credentials: "omit", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ gameIds }) });
}

/** Yalnız user-service medya adresi kabul edilir (sunucudan gelse bile başka kaynağa görsel isteği atılmaz). */
const MEDIA_PATH = /^\/api\/v1\/user\/media\/[0-9a-f]{32}(-s)?\.(jpg|png|gif)$/;
export function safeAvatarPath(value: unknown): string | null {
  return typeof value === "string" && MEDIA_PATH.test(value) ? value : null;
}

export const enrichmentApi = {
  /** `POST /game/games/batch` (≤50). Ulaşılamayan oyun haritada yoktur; arayüz "katalogda yok" gösterir. */
  async gameCards(ids: readonly string[], signal?: AbortSignal): Promise<Map<string, GameCard>> {
    const gameIds = uniqueIds(ids, GAME_BATCH_LIMIT);
    const cards = new Map<string, GameCard>();
    if (gameIds.length === 0) return cards;
    for (const entry of list(record(await publicBatch("/api/v1/game/games/batch", gameIds, signal)).items)) {
      const item = record(entry);
      const gameId = text(item.gameId);
      const title = text(item.title);
      if (!gameId || !title) continue;
      const references = list(item.externalReferences).flatMap((reference) => {
        const ref = record(reference);
        const provider = text(ref.provider);
        const externalId = text(ref.externalId);
        return provider && externalId ? [{ provider, externalId }] : [];
      });
      cards.set(gameId.toLowerCase(), { gameId, title, coverUrl: text(item.coverUrl), externalReferences: references });
    }
    return cards;
  },
  /** `POST /game/games/recommendation-metadata/batch`: tür/platform kodları ve gereksinim platformları. */
  async gameMetadata(ids: readonly string[], signal?: AbortSignal): Promise<Map<string, GameMetadata & { requirementPlatforms: string[] }>> {
    const gameIds = uniqueIds(ids, GAME_BATCH_LIMIT);
    const result = new Map<string, GameMetadata & { requirementPlatforms: string[] }>();
    if (gameIds.length === 0) return result;
    for (const entry of list(record(await publicBatch("/api/v1/game/games/recommendation-metadata/batch", gameIds, signal)).items)) {
      const item = record(entry);
      const gameId = text(item.gameId);
      if (!gameId) continue;
      const codes = (value: unknown) => list(value).flatMap((term) => (text(record(term).code) ? [text(record(term).code) as string] : []));
      const requirementPlatforms = [...new Set(list(item.systemRequirements).flatMap((row) => (text(record(row).platform) ? [text(record(row).platform) as string] : [])))];
      result.set(gameId.toLowerCase(), { gameId, genres: codes(item.genres), platforms: codes(item.platforms), requirementPlatforms });
    }
    return result;
  },
  async gameTerms(kind: "GENRE" | "PLATFORM", signal?: AbortSignal): Promise<CatalogTerm[]> {
    return list(record(await readJson(`/api/v1/game/terms?kind=${kind}`, signal)).items).flatMap((entry) => {
      const item = record(entry);
      const code = text(item.code);
      return code ? [{ code, nameTr: text(item.nameTr), nameEn: text(item.nameEn), gameCount: typeof item.gameCount === "number" ? item.gameCount : 0 }] : [];
    });
  },
  async searchCatalog(query: string, signal?: AbortSignal): Promise<CatalogGame[]> {
    const q = query.trim().slice(0, 128);
    if (!q) return [];
    const params = new URLSearchParams({ q, page: "0", size: "20" });
    return list(record(await readJson(`/api/v1/game/catalog?${params}`, signal)).items).flatMap((entry) => {
      const item = record(entry);
      const gameId = text(item.gameId);
      const title = text(item.title);
      return gameId && title && isUuid(gameId) ? [{ gameId, title }] : [];
    });
  },
  /** `POST /user/profiles/batch` (≤50/istek, CSRF). Engelli/olmayan hesaplar yanıtta yoktur. */
  async profileCards(ids: readonly string[], signal?: AbortSignal): Promise<Map<string, ProfileCard>> {
    const unique = [...new Set(ids.filter(isUuid))];
    const cards = new Map<string, ProfileCard>();
    const responses = await Promise.all(chunks(unique, PROFILE_BATCH_LIMIT).map((userIds) => writeJson("POST", "/api/v1/user/profiles/batch", { userIds }, signal)));
    for (const response of responses) {
      for (const entry of list(record(response).items)) {
        const item = record(entry);
        const userId = text(item.userId);
        if (!userId) continue;
        const avatar = record(item.avatar);
        cards.set(userId.toLowerCase(), { userId, username: text(item.username), displayName: text(item.displayName), avatarPath: safeAvatarPath(avatar.small ?? avatar.large) });
      }
    }
    return cards;
  },
  async getConsent(signal?: AbortSignal): Promise<RecommendationConsent> {
    return toConsent(await readJson("/api/v1/user/me/consents", signal));
  },
  /** Yalnız `version` + `recommendation` gönderilir; 409 `CONCURRENT_MODIFICATION` çağırana iletilir. */
  async saveConsent(consent: RecommendationConsent, signal?: AbortSignal): Promise<RecommendationConsent> {
    return toConsent(await writeJson("PUT", "/api/v1/user/me/consents", { version: consent.version, recommendation: consent.recommendation }, signal));
  },
  async sendFriendRequest(receiverId: string, signal?: AbortSignal): Promise<void> {
    await writeJson("POST", "/api/v1/social/friend-requests", { receiverId }, signal);
  },
  async joinEvent(eventId: string, signal?: AbortSignal): Promise<EventParticipation> {
    const item = record(await writeJson("POST", `/api/v1/event/events/${encodeURIComponent(eventId)}/participations`, {}, signal));
    return { id: text(item.id) ?? "", eventId: text(item.eventId) ?? eventId, status: text(item.status) ?? "JOINED" };
  }
};

/** Bilinmeyen gövde "rıza yok" sayılır (açık rıza varsayılmaz). */
export function toConsent(body: unknown): RecommendationConsent {
  const item = record(body);
  const version = typeof item.version === "number" && Number.isInteger(item.version) && item.version >= 0 ? item.version : 0;
  return { recommendation: item.recommendation === true, version };
}
