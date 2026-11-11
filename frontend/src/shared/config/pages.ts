import type { AppPathname } from "@/shared/lib/i18n/routing";

/**
 * Gerçekten var olan, herkese açık sayfalar — tek kaynak.
 * Footer, global arama, sitemap ve robots bu listeden beslenir.
 * - Başlıklar `Pages.<id>.title` mesajındadır (TR/EN).
 * - Yeni sayfa eklerken önce rotayı yaz, sonra buraya ekle; olmayan rota buraya yazılmaz.
 * - `indexable: false` sayfalar sitemap'e girmez ve `noindex` yayınlanır.
 */
export type PageId =
  | "library"
  | "libraryFavorites"
  | "libraryCompleted"
  | "libraryAdd"
  | "libraryPrivacy"
  | "home"
  | "games"
  | "about"
  | "faq"
  | "contact"
  | "kvkk"
  | "privacy"
  | "terms"
  | "cookies"
  | "settings"
  | "hardware"
  | "campaigns"
  | "computers"
  | "performance"
  | "performanceDashboard";

/** Footer sütunları: platform, destek ve alt satırdaki yasal bağlantılar. */
export type PageSection = "platform" | "support" | "legal";

export type PageEntry = {
  id: PageId;
  /** Dahili yol (`routing.ts` anahtarı); görünen adres dile göre `pathnames` ile üretilir. */
  href: AppPathname;
  section?: PageSection;
  indexable: boolean;
  /** Aramada eşleşecek ek sözcükler (TR ve EN karışık olabilir). */
  keywords: readonly string[];
};

export const pages: readonly PageEntry[] = [
  { id: "performanceDashboard", href: "/performance/dashboard", indexable: false, keywords: ["performans paneli", "dashboard", "grafik", "rapor"] },
  { id: "performance", href: "/performance", indexable: false, keywords: ["performance", "performans", "fps", "cihaz"] },
  { id: "library", href: "/library", indexable: false, keywords: ["kütüphane", "library", "koleksiyon"] },
  { id: "libraryFavorites", href: "/library/favorites", indexable: false, keywords: ["favoriler", "favorites"] },
  { id: "libraryCompleted", href: "/library/completed", indexable: false, keywords: ["bitirilenler", "completed"] },
  { id: "libraryAdd", href: "/library/add", indexable: false, keywords: ["oyun ekle", "add game"] },
  { id: "libraryPrivacy", href: "/library/privacy", indexable: false, keywords: ["kütüphane gizliliği", "library privacy"] },
  { id: "games", href: "/games", indexable: false, keywords: ["oyun", "games", "katalog", "catalogue"] },
  { id: "hardware", href: "/hardware", indexable: false, keywords: ["donanım", "hardware", "cpu", "gpu", "katalog"] },
  { id: "campaigns", href: "/hardware/campaigns", indexable: false, keywords: ["kampanya", "campaign", "indirim", "fırsat"] },
  { id: "computers", href: "/profile/computers", indexable: false, keywords: ["bilgisayarlarım", "computers", "profil", "bileşen"] },
  {
    id: "home",
    href: "/",
    section: "platform",
    indexable: true,
    keywords: ["home", "anasayfa", "başlangıç"]
  },
  {
    id: "about",
    href: "/about",
    section: "platform",
    indexable: true,
    keywords: ["about", "hakkımızda", "lobbytwozero", "ltz"]
  },
  {
    id: "faq",
    href: "/faq",
    section: "support",
    indexable: true,
    keywords: ["faq", "sss", "sıkça sorulan sorular", "yardım", "help"]
  },
  {
    id: "contact",
    href: "/contact",
    section: "support",
    indexable: false,
    keywords: ["contact", "iletişim", "destek", "support"]
  },
  {
    id: "kvkk",
    href: "/legal/kvkk",
    section: "legal",
    indexable: false,
    keywords: ["kvkk", "aydınlatma", "kişisel veri", "legal", "yasal"]
  },
  {
    id: "privacy",
    href: "/legal/privacy",
    section: "legal",
    indexable: false,
    keywords: ["gizlilik", "privacy", "legal", "yasal"]
  },
  {
    id: "terms",
    href: "/legal/terms",
    section: "legal",
    indexable: false,
    keywords: ["kullanım koşulları", "terms", "şartlar", "legal", "yasal"]
  },
  {
    id: "cookies",
    href: "/legal/cookies",
    section: "legal",
    indexable: false,
    keywords: ["çerez", "cookie", "cookies", "legal", "yasal"]
  },
  {
    id: "settings",
    href: "/settings",
    section: "platform",
    indexable: false,
    keywords: ["settings", "ayarlar", "tema", "theme", "dil", "language", "görünüm", "appearance"]
  }
];

export const platformPages = pages.filter((page) => page.section === "platform");
export const supportPages = pages.filter((page) => page.section === "support");
export const legalPages = pages.filter((page) => page.section === "legal");

export function findPage(pathname: AppPathname): PageEntry | undefined {
  return pages.find((page) => page.href === pathname);
}

export function findPageById(id: PageId): PageEntry {
  const page = pages.find((entry) => entry.id === id);
  if (!page) {
    throw new Error(`Bilinmeyen sayfa kimliği: ${id}`);
  }
  return page;
}
