import type { IconName } from "@/shared/components/ui/icons";

/**
 * Global kenar çubuğu navigasyonu (ana menü; Ayarlar bağlantısı ve daralt/genişlet alt bölümdedir).
 * - `href` yalnız gerçekten var olan rotalar için yazılır. `href` olmayan öğe henüz
 *   yayınlanmamıştır; "Yakında" olarak, tıklanamaz biçimde gösterilir (kırık link üretilmez).
 * - Donanım Detayı katalogdan açılır; ayrı menü öğesi değildir.
 * - Rota hazır olduğunda yalnız ilgili öğeye `href` eklenir.
 * - Profil, Bilgisayarlarım ve Kampanyalar menüden çıkarıldı; rota/bileşen silinmedi (henüz yoktu).
 */
export type NavLabelKey = "home" | "hardware" | "settings";

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

/** Alt bölüm: Ayarlar ayrı bir sayfadır (diyalog değil); ana gezinme listesinde yer almaz. */
export const settingsNavigation: NavItem = {
  id: "settings",
  labelKey: "settings",
  icon: "settings",
  href: "/settings"
};

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
