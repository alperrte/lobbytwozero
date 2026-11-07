import { defineRouting } from "next-intl/routing";
import { defaultLocale, localeCookieName, locales } from "./config";

/**
 * Dil önekli rota sözleşmesi (tek strateji): her URL dil önekiyle başlar (`/tr/...`, `/en/...`).
 *
 * - Anahtarlar DAHİLİ yollardır (klasör adları, `src/app/[locale]/(public)/...`). Kod, link ve test
 *   her zaman bu dahili yolu kullanır; kullanıcıya görünen adres `pathnames` ile dil bazında üretilir.
 * - Türkçe sitede Türkçe slug: `/tr/hakkimizda`, `/tr/sss`, `/tr/iletisim`, `/tr/ayarlar`, `/tr/yasal/...`.
 *   İngilizce sitede İngilizce slug: `/en/about`, `/en/faq`, `/en/contact`, `/en/settings`, `/en/legal/...`.
 * - Yeni sayfa eklerken önce klasörü aç, sonra buraya iki dilde slug yaz (`pages.ts` ile birlikte).
 * - Dil çerezi adı eski sözleşmeyle aynı (`locale`); next-intl proxy'si onu yönetir.
 */
export const pathnames = {
  "/": "/",
  "/games": { tr: "/oyunlar", en: "/games" },
  "/games/manage": { tr: "/oyunlar/yonetim", en: "/games/manage" },
  "/games/[gameId]": { tr: "/oyunlar/[gameId]", en: "/games/[gameId]" },
  "/about": { tr: "/hakkimizda", en: "/about" },
  "/faq": { tr: "/sss", en: "/faq" },
  "/contact": { tr: "/iletisim", en: "/contact" },
  "/settings": { tr: "/ayarlar", en: "/settings" },
  "/login": { tr: "/giris", en: "/login" },
  "/register": { tr: "/kayit", en: "/register" },
  "/verify-email": { tr: "/eposta-dogrulama", en: "/verify-email" },
  "/forgot-password": { tr: "/sifremi-unuttum", en: "/forgot-password" },
  // Auth e-postalarındaki bağlantı yolu (dil öneksiz, token URL parçasında); docs-auth/AUTH_REHBERI.md §8.
  "/auth/reset-password": { tr: "/auth/sifre-sifirla", en: "/auth/reset-password" },
  "/account/cancel-deletion": { tr: "/hesap/silmeyi-iptal-et", en: "/account/cancel-deletion" },
  "/account/delete": { tr: "/hesap/sil", en: "/account/delete" },
  "/auth/callback": { tr: "/auth/geri-donus", en: "/auth/callback" },
  "/legal/kvkk": { tr: "/yasal/kvkk", en: "/legal/kvkk" },
  "/legal/privacy": { tr: "/yasal/gizlilik", en: "/legal/privacy" },
  "/legal/terms": { tr: "/yasal/kullanim-kosullari", en: "/legal/terms" },
  "/legal/cookies": { tr: "/yasal/cerezler", en: "/legal/cookies" }
} as const;

/** Uygulamadaki dahili sayfa yolu (ör. "/about"); görünen adres dile göre değişir. */
export type AppPathname = Exclude<keyof typeof pathnames, `${string}[${string}`>;

export const routing = defineRouting({
  locales,
  defaultLocale,
  localePrefix: "always",
  localeCookie: {
    name: localeCookieName,
    maxAge: 60 * 60 * 24 * 365,
    sameSite: "lax"
  },
  pathnames
});
