import type { IconName } from "@/shared/components/ui/icons";

/**
 * Global kenar çubuğu navigasyonu (yalnız ana menü; Ayarlar ve daralt/genişlet alt bölümdedir).
 * - `href` yalnız gerçekten var olan rotalar için yazılır. `href` olmayan öğe henüz
 *   yayınlanmamıştır; "Yakında" olarak, tıklanamaz biçimde gösterilir (kırık link üretilmez).
 * - Donanım Detayı katalogdan açılır; ayrı menü öğesi değildir.
 * - Rota hazır olduğunda yalnız ilgili öğeye `href` eklenir.
 * - Profil, Bilgisayarlarım ve Kampanyalar menüden çıkarıldı; rota/bileşen silinmedi (henüz yoktu).
 */
export type NavLabelKey = "home" | "hardware";

export type NavItem = {
  id: string;
  labelKey: NavLabelKey;
  icon: IconName;
  href?: string;
};

export const primaryNavigation: readonly NavItem[] = [
  { id: "home", labelKey: "home", icon: "home", href: "/" },
  { id: "hardware", labelKey: "hardware", icon: "cpu" }
];

export function isActivePath(pathname: string, href: string): boolean {
  if (href === "/") {
    return pathname === "/";
  }
  return pathname === href || pathname.startsWith(`${href}/`);
}

export function findActiveItem(
  pathname: string,
  items: readonly NavItem[] = primaryNavigation
): NavItem | undefined {
  return items.find((item) => item.href && isActivePath(pathname, item.href));
}
