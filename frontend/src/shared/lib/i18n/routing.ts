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
  "/library": { tr: "/kutuphane", en: "/library" },
  "/library/favorites": { tr: "/kutuphane/favoriler", en: "/library/favorites" },
  "/library/completed": { tr: "/kutuphane/bitirilenler", en: "/library/completed" },
  "/library/add": { tr: "/kutuphane/oyun-ekle", en: "/library/add" },
  "/library/privacy": { tr: "/kutuphane/gizlilik", en: "/library/privacy" },
  "/library/users/[userId]": { tr: "/kutuphane/oyuncu/[userId]", en: "/library/users/[userId]" },
  "/games": { tr: "/oyunlar", en: "/games" },
  "/games/manage": { tr: "/oyunlar/yonetim", en: "/games/manage" },
  "/games/[gameId]": { tr: "/oyunlar/[gameId]", en: "/games/[gameId]" },
  "/about": { tr: "/hakkimizda", en: "/about" },
  "/faq": { tr: "/sss", en: "/faq" },
  "/contact": { tr: "/iletisim", en: "/contact" },
  "/settings": { tr: "/ayarlar", en: "/settings" },
  "/friends": { tr: "/arkadaslar", en: "/friends" },
  "/friends/requests": { tr: "/arkadaslar/istekler", en: "/friends/requests" },
  "/friends/sent": { tr: "/arkadaslar/gonderilenler", en: "/friends/sent" },
  "/friends/blocked": { tr: "/arkadaslar/engellenenler", en: "/friends/blocked" },
  "/profile": { tr: "/profil", en: "/profile" },
  "/profile/edit": { tr: "/profil/duzenle", en: "/profile/edit" },
  "/profile/settings": { tr: "/profil/ayarlar", en: "/profile/settings" },
  "/players": { tr: "/oyuncular", en: "/players" },
  "/players/[userId]": { tr: "/oyuncular/[userId]", en: "/players/[userId]" },
  "/onboarding": { tr: "/baslangic", en: "/onboarding" },
  "/login": { tr: "/giris", en: "/login" },
  "/register": { tr: "/kayit", en: "/register" },
  "/verify-email": { tr: "/eposta-dogrulama", en: "/verify-email" },
  "/forgot-password": { tr: "/sifremi-unuttum", en: "/forgot-password" },
  // Auth e-postalarındaki bağlantı yolu (dil öneksiz, token URL parçasında); docs-auth/AUTH_REHBERI.md §8.
  "/auth/reset-password": { tr: "/auth/sifre-sifirla", en: "/auth/reset-password" },
  "/account/cancel-deletion": { tr: "/hesap/silmeyi-iptal-et", en: "/account/cancel-deletion" },
  "/account/delete": { tr: "/hesap/sil", en: "/account/delete" },
  "/auth/callback": { tr: "/auth/geri-donus", en: "/auth/callback" },
  "/hardware": { tr: "/donanimlar", en: "/hardware" },
  "/hardware/campaigns": { tr: "/donanimlar/kampanyalar", en: "/hardware/campaigns" },
  "/hardware/compare": { tr: "/donanimlar/karsilastir", en: "/hardware/compare" },
  "/hardware/[productId]": { tr: "/donanimlar/[productId]", en: "/hardware/[productId]" },
  "/profile/computers": { tr: "/profil/bilgisayarlarim", en: "/profile/computers" },
  "/community": { tr: "/topluluk", en: "/community" },
  "/community/new": { tr: "/topluluk/yeni", en: "/community/new" },
  "/community/mine": { tr: "/topluluk/gonderilerim", en: "/community/mine" },
  "/community/mine/reposts": { tr: "/topluluk/gonderilerim/yeniden-paylasimlarim", en: "/community/mine/reposts" },
  "/community/forums": { tr: "/topluluk/forumlar", en: "/community/forums" },
  "/community/forums/[forumId]": { tr: "/topluluk/forumlar/[forumId]", en: "/community/forums/[forumId]" },
  "/community/posts/[postId]": { tr: "/topluluk/gonderi/[postId]", en: "/community/posts/[postId]" },
  "/community/users/[userId]": { tr: "/topluluk/oyuncu/[userId]", en: "/community/users/[userId]" },
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
