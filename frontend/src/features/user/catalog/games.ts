/**
 * Favori tür ve platform kataloğu (istemci tarafı). User servisi bu alanlarda yalnız kod BİÇİMİNİ doğrular
 * (`^[a-z0-9][a-z0-9_-]{0,47}$`, en çok 10 kod); anlamı katalog değil, bu liste verir. Game servisi katalog
 * yayınlayınca kodlar oradan alınır ve bu dosya kalkar (bkz. docs-user/integration/frontend/frontend-requests-01.md §6).
 * Etiketler `Profile.catalog.*` mesajlarındadır; kaydı olmayan (eski, serbest yazılmış) kodlar olduğu gibi gösterilir.
 */
export const GENRE_CODES = [
  "action",
  "adventure",
  "rpg",
  "action-rpg",
  "jrpg",
  "strategy",
  "rts",
  "turn-based",
  "grand-strategy",
  "4x",
  "tower-defense",
  "auto-battler",
  "deck-builder",
  "simulation",
  "city-builder",
  "farming",
  "management",
  "racing",
  "sports",
  "fighting",
  "shooter",
  "fps",
  "tps",
  "battle-royale",
  "mmo",
  "moba",
  "survival",
  "horror",
  "puzzle",
  "platformer",
  "metroidvania",
  "roguelike",
  "soulslike",
  "sandbox",
  "open-world",
  "stealth",
  "hack-and-slash",
  "arcade",
  "rhythm",
  "card-game",
  "party",
  "coop",
  "narrative",
  "visual-novel",
  "point-and-click",
  "walking-sim",
  "idle",
  "casual",
  "indie",
  "retro",
  "fantasy",
  "sci-fi",
  "space",
  "vr",
  "educational"
] as const;

export type PlatformGroup = "store" | "pc" | "console" | "mobile" | "vr" | "cloud";

export const PLATFORMS: ReadonlyArray<{ code: string; group: PlatformGroup }> = [
  { code: "steam", group: "store" },
  { code: "epic-games", group: "store" },
  { code: "ea-app", group: "store" },
  { code: "gog", group: "store" },
  { code: "ubisoft-connect", group: "store" },
  { code: "battle-net", group: "store" },
  { code: "riot-client", group: "store" },
  { code: "xbox-app", group: "store" },
  { code: "microsoft-store", group: "store" },
  { code: "rockstar-launcher", group: "store" },
  { code: "amazon-games", group: "store" },
  { code: "itch-io", group: "store" },
  { code: "pc", group: "pc" },
  { code: "mac", group: "pc" },
  { code: "linux", group: "pc" },
  { code: "steam-deck", group: "pc" },
  { code: "playstation-5", group: "console" },
  { code: "playstation-4", group: "console" },
  { code: "xbox-series", group: "console" },
  { code: "xbox-one", group: "console" },
  { code: "nintendo-switch", group: "console" },
  { code: "nintendo-switch-2", group: "console" },
  { code: "retro-console", group: "console" },
  { code: "android", group: "mobile" },
  { code: "ios", group: "mobile" },
  { code: "meta-quest", group: "vr" },
  { code: "pc-vr", group: "vr" },
  { code: "geforce-now", group: "cloud" },
  { code: "xbox-cloud", group: "cloud" }
];

export const PLATFORM_GROUPS: readonly PlatformGroup[] = ["store", "pc", "console", "mobile", "vr", "cloud"];

/** Sunucu sınırı: tür ve platform listelerinin her biri en çok bu kadar kod taşır. */
export const MAX_FAVORITES = 10;
