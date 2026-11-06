import { primaryNavigation, type NavLabelKey } from "./navigation";
import { findPageById, type PageId } from "./pages";

/**
 * Footer ürün navigasyonu: dört grup, her grupta üç öğe.
 *
 * - Ürün öğeleri (`source: "navigation"`) sidebar ile AYNI kaynaktan (`primaryNavigation`) okunur:
 *   isim ve `href` oradan gelir. Bir özelliğin rotası `navigation.ts` içinde yazıldığında footer'daki
 *   öğe de kendiliğinden bağlantıya dönüşür; burada ayrıca bir değişiklik gerekmez.
 * - `href` olmayan öğe henüz yayınlanmamıştır: footer'da adı görünür ama bağlantı olmaz
 *   (sahte rota, kırık link ve "Yakında" rozeti üretilmez).
 * - Destek öğeleri (`source: "page"`) `pages.ts` içindeki gerçek sayfalardır.
 * - Yasal bağlantılar bu listede değildir; alt satırda `legalPages` ile gösterilir.
 */
export type FooterGroupId = "discover" | "community" | "performance" | "support";

export type FooterEntry =
  | { source: "navigation"; id: NavLabelKey; href?: string }
  | { source: "page"; id: PageId; href: string };

export type FooterGroup = {
  id: FooterGroupId;
  entries: readonly FooterEntry[];
};

function fromNavigation(id: NavLabelKey): FooterEntry {
  const item = primaryNavigation.find((candidate) => candidate.id === id);
  if (!item) {
    throw new Error(`Footer için bilinmeyen gezinme öğesi: ${id}`);
  }
  return { source: "navigation", id, href: item.href };
}

function fromPage(id: PageId): FooterEntry {
  return { source: "page", id, href: findPageById(id).href };
}

export const footerGroups: readonly FooterGroup[] = [
  {
    id: "discover",
    entries: [fromNavigation("games"), fromNavigation("news"), fromNavigation("reviews")]
  },
  {
    id: "community",
    entries: [
      fromNavigation("community"),
      fromNavigation("findTeam"),
      fromNavigation("events")
    ]
  },
  {
    id: "performance",
    entries: [
      fromNavigation("hardware"),
      fromNavigation("performance"),
      fromNavigation("forYou")
    ]
  },
  {
    id: "support",
    entries: [fromPage("about"), fromPage("faq"), fromPage("contact")]
  }
];
