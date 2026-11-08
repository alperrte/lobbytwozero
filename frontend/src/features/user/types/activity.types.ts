/**
 * Profil sekmelerinin diğer servislerden okuduğu veriler. Hepsi arayüzün ihtiyacı kadar daraltılmış, savunmacı okunmuş
 * biçimlerdir (bilinmeyen alanlar yok sayılır); sayı bilinmiyorsa uydurulmaz, ilgili alan `null` olur.
 */

/** `GET /api/v1/library/entries/statistics`: kendi koleksiyonun sayıları. */
export type LibraryStats = { total: number; favorite: number; completed: number };

/** `GET /api/v1/social/friends` bir satırı. Ad ve avatar User'dadır, burada yalnız kimlik gelir. */
export type FriendEntry = { friendId: string; since: string | null };
/** `hasMore`: sunucu bu sayfadan sonra da arkadaş olduğunu söylüyor (toplam sayı verilmez). */
export type FriendsPage = { items: FriendEntry[]; hasMore: boolean };

/** `POST /api/v1/user/profiles/batch` kartı. */
export type PlayerCard = { userId: string; username: string | null; displayName: string | null; avatar: string | null };

/** `GET /api/v1/community/users/{userId}/posts` öğesinin profilde gösterilen kısmı. */
export type RecentPost = {
  id: string;
  title: string | null;
  body: string | null;
  createdAt: string | null;
  likeCount: number;
  commentCount: number;
  isRepost: boolean;
  /** Yalnız sahibi görür: moderasyonla gizlenmiş gönderi. */
  hidden: boolean;
};

export type ComputerType = "DESKTOP" | "LAPTOP" | "OTHER";

/** `GET /api/v1/hardware/profiles/statistics`. Kapasite yalnız kayıtlı değerlerin toplamıdır; eksikler ayrı sayılır. */
export type HardwareSummary = {
  computers: number;
  byType: Partial<Record<ComputerType, number>>;
  componentUnits: number;
  ramGb: number;
  ramUnitsWithoutCapacity: number;
  storageGb: number;
  storageUnitsWithoutCapacity: number;
};

export type PerformanceMetric = {
  metric: string;
  unit: string | null;
  sessions: number;
  average: number | null;
  min: number | null;
  max: number | null;
};

/** `GET /api/v1/performance/stats`: yalnız kendi oturumlarının özeti. */
export type PerformanceSummary = { sessions: number; samples: number; metrics: PerformanceMetric[] };
