export type LibraryEntry = {
  id: string; gameId: string; inCollection: boolean; favorite: boolean; completed: boolean;
  addedAt: string; updatedAt: string; version: number;
};
export type LibraryGame = {
  gameId: string; title: string | null; coverUrl: string | null; origin: string | null;
  metadataCompleteness: string | null; gameType: string | null;
  status: "AVAILABLE" | "UNAVAILABLE" | "TEMPORARILY_UNAVAILABLE";
  metadataSource: "LIVE" | "LAST_KNOWN" | "NONE"; metadataRefreshedAt: string | null;
};
export type LibraryItem = { entry: LibraryEntry; game: LibraryGame };
export type LibraryPage = { items: LibraryItem[]; page: number; size: number; totalElements: number; metadataPartial: boolean };
export type LibraryStatistics = { totalGames: number; favoriteGames: number; completedGames: number };
export type LibraryQuery = { favorite?: boolean; completed?: boolean; sort: "ADDED_AT" | "TITLE"; direction: "ASC" | "DESC"; page: number; size: number };
export type LibraryView = "all" | "favorites" | "completed";
export type Visibility = "PUBLIC" | "FRIENDS" | "PRIVATE";
export type LibraryPrivacy = { version: number; friendsList: Visibility; library: Visibility; hardware: Visibility; performance: Visibility; guestProfileAccess: "BASIC" | "DETAILED" };
export type LibraryMutation =
  | { kind: "add" | "remove"; gameId: string }
  | { kind: "favorite" | "completed"; gameId: string; enabled: boolean }
  | { kind: "privacy"; settings: LibraryPrivacy; library: Visibility };
export type LibraryResource =
  | { kind: "collection"; query: LibraryQuery }
  | { kind: "statistics" }
  | { kind: "profile"; userId: string; page: number; size: number }
  | { kind: "privacy" };
