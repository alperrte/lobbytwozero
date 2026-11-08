/**
 * Social Service tipleri.
 *
 * İki ayrı grup vardır:
 * 1. **API sözleşmesi** (`Friend`, `FriendRequest`, `Block`, `InviteCode`, `CursorPage`): social-service yanıtlarıyla birebir
 *    aynıdır (`docs/api.md` SOCIAL). Kimlikler UUID, zamanlar ISO-8601 (UTC) metindir.
 * 2. **Görünüm zenginleştirmesi** (`SocialProfile`, `Presence`, `GameActivity`): social-service bu bilgileri TUTMAZ.
 *    Ad, kullanıcı adı ve avatar user-service toplu profil kartından (`POST /api/v1/user/profiles/batch`) gelir.
 *    Çevrimiçi/oyunda durumu, oynanan oyun ve ortak arkadaş sayısının henüz kaynağı yoktur; önizleme verisiyle
 *    doldurulur (`services/social-preview.ts`).
 */

export type UserId = string;

/** `GET /api/v1/social/*` liste uçlarının ortak imleçli sayfa biçimi. `nextCursor` null ise son sayfadır. */
export type CursorPage<T> = {
  items: T[];
  nextCursor: string | null;
};

export type Friend = {
  friendId: UserId;
  since: string;
};

export type FriendRequestStatus = "PENDING" | "ACCEPTED" | "REJECTED" | "CANCELLED";

export type FriendRequest = {
  id: string;
  senderId: UserId;
  receiverId: UserId;
  status: FriendRequestStatus;
  createdAt: string;
  respondedAt: string | null;
};

export type Block = {
  blockedUserId: UserId;
  createdAt: string;
};

export type InviteCode = {
  code: string;
  createdAt: string;
  expiresAt: string;
  maxUses: number;
  usedCount: number;
};

export type PageQuery = {
  limit?: number;
  cursor?: string | null;
};

/* ---------- Görünüm zenginleştirmesi (social-service dışı) ---------- */

export type SocialProfile = {
  userId: UserId;
  /**
   * Görünen ad. `null`: user-service kartı vermedi (hesap silinmiş/yasaklı ya da aranızda engel var; user-service bunları
   * sessizce atlar) veya kullanıcı ad belirlememiş. Arayüz bu durumda yedek ad gösterir.
   */
  displayName: string | null;
  username: string | null;
  /** Gateway'e göre göreli küçük avatar yolu (`/api/v1/user/media/...`); yoksa `null`. */
  avatarPath: string | null;
  /** Avatar fotoğrafı yoksa çizilecek simgenin tohumu. */
  avatarSeed: string;
  /** Ortak arkadaş sayısı; kaynağı yoktur (önizleme verisi). */
  mutualFriends: number;
};

export type PresenceStatus = "online" | "inGame" | "offline";

/** Oyundaki etkinlik türü; metni çeviri dosyasından gelir (`Social.activity.*`). */
export type ActivityKind = "ranked" | "competitive" | "story" | "exploring" | "coop" | "casual";

export type GameVisual = {
  /** Vurgu tonu: yalnız marka renkleri. */
  tone: "primary" | "secondary" | "accent";
  /** Kapak yer tutucusundaki geometrik desen. */
  glyph: "chevron" | "ring" | "diamond" | "bolt" | "grid";
};

export type GameActivity = {
  gameId: string;
  title: string;
  activity: ActivityKind;
  visual: GameVisual;
};

export type Presence = {
  status: PresenceStatus;
  /** Şu an oynanan ya da en son oynanan oyun. */
  game: GameActivity | null;
  /** Çevrimdışıysa son görülme zamanı (ISO-8601). */
  lastSeenAt: string | null;
};

/** Kart ve satırların gösterdiği birleşik görünüm: kimlik + profil + durum. */
export type SocialPerson = {
  profile: SocialProfile;
  presence: Presence;
};

export type FriendView = SocialPerson & { friend: Friend };
export type FriendRequestView = SocialPerson & { request: FriendRequest };
export type BlockView = SocialPerson & { block: Block };

/** Sekme rozeti: `more` doğruysa gerçek sayı `count`'tan büyüktür ("50+" gösterilir). Serviste sayım ucu yoktur. */
export type SocialCount = { count: number; more: boolean };

export type SocialCounts = {
  friends: SocialCount;
  incoming: SocialCount;
  outgoing: SocialCount;
  blocked: SocialCount;
};
