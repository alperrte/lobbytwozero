import type { PlayerType, ProfileImage, ProfileStats } from "./profile.types";

/** Açık profildeki Steam bağlantısı; yalnız sahibi profilde göstermeyi seçtiyse gelir. */
export type PublicSteam = { steamId: string; profileUrl: string };

/**
 * Ayrıntılar. Sunucu bunları yalnız izleyicinin görmesine izin verdiğinde (üye, arkadaş ya da sahibin açtığı misafir erişimi)
 * gönderir; aksi halde `PublicProfile.details` `null` olur.
 */
export type PublicDetails = {
  bio: string | null;
  country: string | null;
  playerTypes: PlayerType[];
  favoriteGenres: string[];
  favoritePlatforms: string[];
  steam: PublicSteam | null;
};

/** İzleyicinin görebileceği bölümler. Sayfa yalnız bunlar için ek veri ister. */
export type VisibleSections = { friendsList: boolean; library: boolean; hardware: boolean; performance: boolean };

/** `GET /api/v1/user/profiles/{userId}` ve `/by-username/{username}`: başkasının açık profili. */
export type PublicProfile = {
  userId: string;
  username: string | null;
  displayName: string | null;
  avatar: ProfileImage | null;
  cover: ProfileImage | null;
  details: PublicDetails | null;
  visible: VisibleSections;
  summary: ProfileStats;
};
