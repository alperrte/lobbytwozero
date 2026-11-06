/**
 * Gerçekten var olan, herkese açık sayfalar — tek kaynak.
 * Footer, global arama, navbar sayfa adı, sitemap ve robots bu listeden beslenir.
 * - Başlıklar `Pages.<id>.title` mesajındadır (TR/EN).
 * - Yeni sayfa eklerken önce rotayı yaz, sonra buraya ekle; olmayan rota buraya yazılmaz.
 * - `indexable: false` sayfalar sitemap'e girmez ve `noindex` yayınlanır.
 */
export type PageId =
  | "home"
  | "about"
  | "faq"
  | "contact"
  | "kvkk"
  | "privacy"
  | "terms"
  | "cookies";

export type PageSection = "platform" | "legal";

export type PageEntry = {
  id: PageId;
  href: string;
  section?: PageSection;
  indexable: boolean;
  /** Aramada eşleşecek ek sözcükler (TR ve EN karışık olabilir). */
  keywords: readonly string[];
};

export const pages: readonly PageEntry[] = [
  { id: "home", href: "/", indexable: true, keywords: ["home", "anasayfa", "başlangıç"] },
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
    section: "platform",
    indexable: true,
    keywords: ["faq", "sss", "sıkça sorulan sorular", "yardım", "help"]
  },
  {
    id: "contact",
    href: "/contact",
    section: "platform",
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
  }
];

export const platformPages = pages.filter((page) => page.section === "platform");
export const legalPages = pages.filter((page) => page.section === "legal");

export function findPage(pathname: string): PageEntry | undefined {
  return pages.find((page) => page.href === pathname);
}

export function findPageById(id: PageId): PageEntry {
  const page = pages.find((entry) => entry.id === id);
  if (!page) {
    throw new Error(`Bilinmeyen sayfa kimliği: ${id}`);
  }
  return page;
}
