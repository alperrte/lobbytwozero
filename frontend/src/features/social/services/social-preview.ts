import type { ActivityKind, GameActivity, GameVisual, Presence, PresenceStatus, UserId } from "../types/social.types";

/**
 * ÖNİZLEME VERİSİ — kaynağı henüz olmayan alanlar (ürün kararı: sahte veriyle gösterilir).
 *
 * Çevrimiçi/oyunda durumu, oynanan oyun ve ortak arkadaş sayısı için bir presence kaynağı ya da sayım ucu yoktur. Bu
 * değerler gerçek kullanıcı kimliğinden DETERMİNİSTİK türetilir: aynı kişi her yüklemede aynı görünür, rastgele zıplamaz.
 * Başlıktaki "Önizleme verisi" rozeti bu dosya kullanıldığı sürece açık kalır (`SOCIAL_PREVIEW_DATA`).
 * Oyun adları kurgusaldır; gerçek oyun, marka veya kapak kullanılmaz.
 */

const MINUTE = 60_000;

const games: ReadonlyArray<{ gameId: string; title: string; visual: GameVisual }> = [
  { gameId: "preview-rift-protocol", title: "Rift Protocol", visual: { tone: "primary", glyph: "chevron" } },
  { gameId: "preview-ashen-crown", title: "Ashen Crown", visual: { tone: "secondary", glyph: "ring" } },
  { gameId: "preview-iron-tide", title: "Iron Tide", visual: { tone: "primary", glyph: "bolt" } },
  { gameId: "preview-neon-drift", title: "Neon Drift", visual: { tone: "accent", glyph: "grid" } },
  { gameId: "preview-duo-quest", title: "Duo Quest", visual: { tone: "accent", glyph: "diamond" } },
  { gameId: "preview-hollow-sector", title: "Hollow Sector", visual: { tone: "secondary", glyph: "grid" } },
  { gameId: "preview-skyforge-arena", title: "Skyforge Arena", visual: { tone: "primary", glyph: "diamond" } }
];

const activities: readonly ActivityKind[] = ["ranked", "competitive", "story", "exploring", "coop", "casual"];
const statuses: readonly PresenceStatus[] = ["online", "online", "inGame", "inGame", "offline", "offline", "offline"];
/** Son görülme: 45 dk, 3 sa, 9 sa, 1 gün, 3 gün önce. */
const lastSeenMinutes = [45, 180, 540, 1440, 4320];

/** FNV-1a; yalnız görünüm seçer, güvenlik amacı yoktur. */
function hash(value: string, salt: string): number {
  let result = 0x811c9dc5;
  for (const char of `${salt}:${value}`) {
    result ^= char.charCodeAt(0);
    result = Math.imul(result, 0x01000193);
  }
  return result >>> 0;
}

function pick<T>(values: readonly T[], userId: UserId, salt: string): T {
  return values[hash(userId, salt) % values.length];
}

export function previewPresence(userId: UserId, now: number): Presence {
  const status = pick(statuses, userId, "status");
  const game = hash(userId, "has-game") % 6 === 0 ? null : pick(games, userId, "game");
  const activity: GameActivity | null = game ? { ...game, activity: pick(activities, userId, "activity") } : null;
  return {
    status,
    game: status === "inGame" && !activity ? { ...games[0], activity: "casual" } : activity,
    lastSeenAt: status === "offline" ? new Date(now - pick(lastSeenMinutes, userId, "seen") * MINUTE).toISOString() : null
  };
}

export function previewMutualFriends(userId: UserId): number {
  return hash(userId, "mutual") % 8;
}
