/**
 * TASARIM ÖNİZLEMESİ verisi. Rütbe, rozet, durum mesajı ve imza için henüz backend alanı yoktur
 * (`docs/api.md` → USER). Bu değerler gerçek kullanıcıya ait DEĞİLDİR; arayüzde her zaman "Önizleme" işaretiyle gösterilir ve
 * hiçbir ağ isteğine bağlanmaz. Alanlar backend'e eklenince bu dosya ve ilgili bileşen bağlamaları kaldırılır.
 * Metinler `Profile.preview.*` mesajlarından gelir; burada yalnız dilden bağımsız kimlikler ve tarih durur.
 */
export const previewProfile = {
  rank: "veteran",
  badges: ["earlyBird", "reviewer", "collector", "teamPlayer", "nightOwl", "bugHunter"]
} as const;

export type PreviewBadgeId = (typeof previewProfile.badges)[number];

