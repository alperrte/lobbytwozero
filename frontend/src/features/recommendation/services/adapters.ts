import { normalizeCode } from "../schemas/filters";
import type {
  CatalogTerm,
  EventSuggestion,
  GameCard,
  GameMetadata,
  PlayerSuggestion,
  ProfileCard,
  RecommendedItem
} from "../types/recommendation";

/**
 * Recommendation yanıtı (kimlik + sıra) ile diğer servislerin kartlarını birleştirir. Zenginleştirme eksikse öneri
 * düşürülmez ve sıra bozulmaz: oyun kataloğunda bulunamayan öğe `available: false`, profili alınamayan oyuncu
 * `profile: null` olarak kalır; arayüz bunu dürüstçe ("katalogda yok", "profil alınamadı") gösterir.
 */
export type GameMetadataView = GameMetadata & { requirementPlatforms: string[] };

export type RecommendedGameView = {
  item: RecommendedItem;
  card: GameCard | null;
  metadata: GameMetadataView | null;
  /** Kart sorgusu tamamlandı ve oyun yanıtta yoktu (gizli/silinmiş/yetişkin içerik). Yükleniyorsa `true` kalır. */
  available: boolean;
};

export type EnrichmentState<T> = { data: Map<string, T> | undefined; failed: boolean };

const key = (id: string) => id.toLowerCase();

export function toGameViews(
  items: readonly RecommendedItem[],
  cards: EnrichmentState<GameCard>,
  metadata?: Map<string, GameMetadataView>
): RecommendedGameView[] {
  return items.map((item) => {
    const card = cards.data?.get(key(item.gameId)) ?? null;
    // Sorgu başarısız olduysa oyunun gerçekten yok olduğu bilinmez; "katalogda yok" yalnız başarılı yanıtta söylenir.
    const available = card !== null || cards.data === undefined || cards.failed;
    return { item, card, metadata: metadata?.get(key(item.gameId)) ?? null, available };
  });
}

export type PlayerSuggestionView = { suggestion: PlayerSuggestion; profile: ProfileCard | null };

export function toPlayerViews(items: readonly PlayerSuggestion[], profiles: Map<string, ProfileCard> | undefined): PlayerSuggestionView[] {
  return items.map((suggestion) => ({ suggestion, profile: profiles?.get(key(suggestion.userId)) ?? null }));
}

export type EventSuggestionView = { event: EventSuggestion; game: GameCard | null };

export function toEventViews(items: readonly EventSuggestion[], cards: Map<string, GameCard> | undefined): EventSuggestionView[] {
  return items.map((event) => ({ event, game: event.gameId ? cards?.get(key(event.gameId)) ?? null : null }));
}

/** Kişinin görünen adı: önce görünen ad, sonra kullanıcı adı; ikisi de yoksa `null` (arayüz nötr metin gösterir). */
export function profileName(profile: ProfileCard | null): string | null {
  const name = profile?.displayName?.trim() || profile?.username?.trim();
  return name || null;
}

/** Game Service terimi (`/game/terms`) → yerelleştirilmiş ad; bilinmiyorsa kodun kendisi. */
export function termLabel(terms: readonly CatalogTerm[] | undefined, code: string, locale: string): string {
  const term = terms?.find((entry) => entry.code === code);
  const name = locale === "en" ? term?.nameEn ?? term?.nameTr : term?.nameTr ?? term?.nameEn;
  return name?.trim() || code;
}

/**
 * Event Service ortak tür/platform kodlarını `[a-z0-9]` biçimine indirger (`role-playing` → `roleplaying`).
 * Kullanıcı kataloğundaki anahtarla aynı normalizasyonla eşlenir; eşleşme yoksa `null` (arayüz kodu olduğu gibi gösterir).
 */
export function matchCatalogKey(code: string, keys: readonly string[]): string | null {
  const normalized = normalizeCode(code);
  return keys.find((candidate) => candidate === code) ?? keys.find((candidate) => normalizeCode(candidate) === normalized) ?? null;
}

/** Aynı kimliği iki kez sorgulamamak için tekilleştirilmiş, sırası korunmuş kimlik listesi. */
export function distinctIds(ids: readonly (string | null | undefined)[]): string[] {
  const seen = new Set<string>();
  const result: string[] = [];
  for (const id of ids) {
    if (!id) continue;
    const normalized = key(id);
    if (seen.has(normalized)) continue;
    seen.add(normalized);
    result.push(id);
  }
  return result;
}
