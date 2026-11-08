/** Kabukta (navbar profil menüsü) gösterilen en küçük profil özeti. */
export type ProfileSummary = {
  /** Gösterim adı; yoksa kullanıcı adı; ikisi de yoksa `null` (arayüz genel etiketi kullanır). */
  name: string | null;
  /** Küçük avatarın mutlak adresi; görsel yoksa `null`. */
  avatarUrl: string | null;
};

export const playerTypes = [
  "CASUAL",
  "COMPETITIVE",
  "STORY",
  "SOCIAL",
  "COLLECTOR",
  "EXPLORER",
  "COMPLETIONIST",
  "STRATEGIST",
  "TEAM_PLAYER",
  "CREATIVE",
  "SPEEDRUNNER",
  "HARDCORE"
] as const;
export type PlayerType = (typeof playerTypes)[number];

/** Profil görselinin iki boyutu (mutlak adres). `null`: görsel yok. */
export type ProfileImage = { small: string; large: string };

/** `GET /api/v1/user/me` yanıtından okunan, arayüzün kullandığı profil. */
export type MyProfile = {
  username: string | null;
  displayName: string | null;
  bio: string | null;
  country: string | null;
  playerTypes: PlayerType[];
  favoriteGenres: string[];
  favoritePlatforms: string[];
  avatar: ProfileImage | null;
  cover: ProfileImage | null;
  /** Üyelik tarihi (ISO-8601); sunucu göndermediyse ya da okunamadıysa `null`, tarih uydurulmaz. */
  memberSince: string | null;
  /** Kaydederken sunucuya aynen geri gönderilir (iyimser kilit). */
  version: number;
};

export type MyProfileUpdate = {
  version: number;
  displayName?: string;
  bio?: string;
  country?: string;
  playerTypes?: PlayerType[];
  favoriteGenres?: string[];
  favoritePlatforms?: string[];
};

export type ImageKind = "avatar" | "cover";

/** Kaynak servisten gelen özetin durumu; sayı yalnız `AVAILABLE` iken doludur, uydurulmaz. */
export type SectionStatus = "AVAILABLE" | "HIDDEN" | "UNAVAILABLE";
export type CountSection = { status: SectionStatus; count: number | null };

/** `GET /api/v1/user/profiles/{userId}` içindeki `summary`: arkadaş, oyun koleksiyonu ve inceleme sayıları. */
export type ProfileStats = { friends: CountSection; library: CountSection; reviews: CountSection };

/** Steam bağlantısının profilde gösterilecek hâli. */
export type SteamLink = { linked: boolean; showOnProfile: boolean };
