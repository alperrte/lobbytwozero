import { apiRequest } from "@/shared/lib/api/client";
import type {
  ComputerType,
  FriendsPage,
  HardwareSummary,
  LibraryStats,
  PerformanceSummary,
  RecentPost
} from "../types/activity.types";

/**
 * Profil sekmelerinin okuduğu diğer servis uçları (Library, Social, Community, Hardware, Performance). Hepsi yalnız GET'tir ve
 * ortak `apiRequest` üzerinden gider (oturum yenilemesi dahil). Yanıtlar savunmacı okunur; eksik/bozuk alan 0 ya da `null` olur,
 * bilinmeyen alan yok sayılır. Başka özelliklerin servis dosyaları içe aktarılmaz: her uç burada küçük bir okuyucuyla durur.
 */

function record(value: unknown): Record<string, unknown> {
  return typeof value === "object" && value !== null ? (value as Record<string, unknown>) : {};
}

function count(value: unknown): number {
  return typeof value === "number" && Number.isFinite(value) && value >= 0 ? value : 0;
}

function numberOrNull(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function text(value: unknown): string | null {
  return typeof value === "string" && value.trim() !== "" ? value : null;
}

function date(value: unknown): string | null {
  return typeof value === "string" && !Number.isNaN(Date.parse(value)) ? value : null;
}

function list(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Kendi koleksiyonunun sayıları. */
export async function getLibraryStats(signal?: AbortSignal): Promise<LibraryStats> {
  const r = record(await apiRequest<unknown>("/api/v1/library/entries/statistics", { signal }));
  return { total: count(r.totalGames), favorite: count(r.favoriteGames), completed: count(r.completedGames) };
}

export const FRIENDS_LIMIT = 50;

/** Kendi arkadaş listenin ilk sayfası (en yeni arkadaşlık önce). Toplam sayı verilmez, yalnız "daha var" bilgisi. */
export async function getFriends(signal?: AbortSignal): Promise<FriendsPage> {
  const r = record(await apiRequest<unknown>(`/api/v1/social/friends?limit=${FRIENDS_LIMIT}`, { signal }));
  const items = list(r.items).flatMap((raw) => {
    const item = record(raw);
    return typeof item.friendId === "string" && UUID.test(item.friendId)
      ? [{ friendId: item.friendId, since: date(item.since) }]
      : [];
  });
  return { items, hasMore: typeof r.nextCursor === "string" && r.nextCursor !== "" };
}

export const RECENT_POSTS_LIMIT = 5;

/** Bir oyuncunun son herkese açık gönderileri (kendi hesabında moderasyonla gizlenenler de gelir ve işaretlenir). */
export async function getRecentPosts(userId: string, signal?: AbortSignal): Promise<RecentPost[]> {
  if (!UUID.test(userId)) {
    return [];
  }
  const r = record(
    await apiRequest<unknown>(`/api/v1/community/users/${userId}/posts?limit=${RECENT_POSTS_LIMIT}&sort=NEWEST`, { signal })
  );
  return list(r.items).flatMap((raw) => {
    const item = record(raw);
    if (typeof item.id !== "string" || !UUID.test(item.id) || item.status === "DELETED") {
      return [];
    }
    return [
      {
        id: item.id,
        title: text(item.title),
        body: text(item.body),
        createdAt: date(item.createdAt),
        likeCount: count(item.likeCount),
        commentCount: count(item.commentCount),
        isRepost: item.repostOf !== null && item.repostOf !== undefined,
        hidden: item.status === "HIDDEN"
      }
    ];
  });
}

const computerTypes: readonly ComputerType[] = ["DESKTOP", "LAPTOP", "OTHER"];

/** Kayıtlı bilgisayarlarının özeti (Hardware). */
export async function getHardwareSummary(signal?: AbortSignal): Promise<HardwareSummary> {
  const r = record(await apiRequest<unknown>("/api/v1/hardware/profiles/statistics", { signal }));
  const byType = record(r.profilesByDeviceType);
  return {
    computers: count(r.totalProfiles),
    byType: Object.fromEntries(
      computerTypes.filter((type) => count(byType[type]) > 0).map((type) => [type, count(byType[type])])
    ),
    componentUnits: count(r.totalComponentUnits),
    ramGb: count(r.recordedRamCapacityGb),
    ramUnitsWithoutCapacity: count(r.ramUnitsWithoutCapacity),
    storageGb: count(r.recordedStorageCapacityGb),
    storageUnitsWithoutCapacity: count(r.storageUnitsWithoutCapacity)
  };
}

/** Kendi oyun performansı özetin (yalnız sahibine açık). */
export async function getPerformanceSummary(signal?: AbortSignal): Promise<PerformanceSummary> {
  const r = record(await apiRequest<unknown>("/api/v1/performance/stats", { signal }));
  return {
    sessions: count(r.sessionCount),
    samples: count(r.sampleCount),
    metrics: list(r.metrics).flatMap((raw) => {
      const item = record(raw);
      return typeof item.metric === "string" && item.metric !== ""
        ? [
            {
              metric: item.metric,
              unit: text(item.unit),
              sessions: count(item.sessionCount),
              average: numberOrNull(item.avg),
              min: numberOrNull(item.min),
              max: numberOrNull(item.max)
            }
          ]
        : [];
    })
  };
}
