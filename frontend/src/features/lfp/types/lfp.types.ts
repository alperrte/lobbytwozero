/**
 * LFP Service tipleri (`docs/api.md` → `## LFP`). Kimlikler UUID, zamanlar ISO-8601 (UTC) metindir.
 * Sahibin adı/avatarı ve oyun kapağı LFP'de yoktur; user-service toplu profil kartından ve game-service toplu kartından eklenir.
 */

export const PLATFORMS = ["PC", "PLAYSTATION", "XBOX", "NINTENDO_SWITCH", "MOBILE", "OTHER"] as const;
export type Platform = (typeof PLATFORMS)[number];

export type ListingStatus = "OPEN" | "CLOSED" | "EXPIRED";

export type Listing = {
  id: string;
  ownerId: string;
  gameId: string;
  gameTitle: string;
  platform: Platform;
  skillLevel: string | null;
  roles: string[];
  playersNeeded: number;
  playTimeStart: string | null;
  playTimeEnd: string | null;
  micRequired: boolean;
  description: string;
  status: ListingStatus;
  expiresAt: string;
  createdAt: string;
  updatedAt: string;
  closedAt: string | null;
  version: number;
};

export type ListingPage = { items: Listing[]; nextCursor: string | null };

/** Oluşturma gövdesi; güncellemede `gameId` ve `durationHours` gönderilmez, `version` eklenir. */
export type ListingInput = {
  gameId: string;
  platform: Platform;
  skillLevel?: string;
  roles: string[];
  playersNeeded: number;
  playTimeStart?: string;
  playTimeEnd?: string;
  micRequired: boolean;
  description: string;
  durationHours?: number;
};

export type ListingUpdate = Omit<ListingInput, "gameId" | "durationHours"> & { version: number };

/** Aktif ilan araması (LFP-11). Tüm süzgeçler isteğe bağlıdır ve VE ile uygulanır. */
export type ListingFilters = {
  q?: string;
  gameId?: string;
  platform?: Platform;
  skillLevel?: string;
  role?: string;
  micRequired?: boolean;
  /** Zaman süzgeci ön ayarı; istekte `playFrom`/`playTo` aralığına çevrilir. */
  when?: WhenPreset;
};

export const WHEN_PRESETS = ["today", "tomorrow", "week"] as const;
export type WhenPreset = (typeof WHEN_PRESETS)[number];

export type ContactResult = { listingId: string; recipientId: string };

/** Görünüm için eklenen bilgiler (LFP dışı kaynaklar). */
export type Owner = { userId: string; name: string; handle: string | null; avatarUrl: string | null };
export type GameVisual = { title: string; coverUrl: string | null };
