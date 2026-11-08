/**
 * TASARIM ÖNİZLEMESİ verisi. Rütbe, rozet, üyelik tarihi, durum mesajı ve imza için henüz backend alanı yoktur
 * (`docs/api.md` → USER). Bu değerler gerçek kullanıcıya ait DEĞİLDİR; arayüzde her zaman "Önizleme" işaretiyle gösterilir ve
 * hiçbir ağ isteğine bağlanmaz. Alanlar backend'e eklenince bu dosya ve ilgili bileşen bağlamaları kaldırılır.
 * Metinler `Profile.preview.*` mesajlarından gelir; burada yalnız dilden bağımsız kimlikler ve tarih durur.
 */
export const previewProfile = {
  rank: "veteran",
  /** ISO tarih (UTC), yalnız biçimlendirme gösterimi içindir. */
  memberSince: "2024-03-14",
  badges: ["earlyBird", "reviewer", "collector", "teamPlayer", "nightOwl", "bugHunter"]
} as const;

export type PreviewBadgeId = (typeof previewProfile.badges)[number];

/** Henüz servisi olmayan sekmeler. */
export const previewTabs = ["activity", "games", "friends"] as const;
export type PreviewTabId = (typeof previewTabs)[number];
