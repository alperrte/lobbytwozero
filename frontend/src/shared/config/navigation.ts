import type { IconName } from "@/shared/components/ui/icons";

/**
 * Global kenar çubuğu navigasyonu.
 * - `href` yalnız gerçekten var olan rotalar için yazılır. `href` olmayan öğe henüz
 *   yayınlanmamıştır; "Yakında" olarak, tıklanamaz biçimde gösterilir (kırık link üretilmez).
 * - Donanım Detayı katalogdan açılır; ayrı menü öğesi değildir.
 * - Rota hazır olduğunda yalnız ilgili öğeye `href` eklenir.
 */
export type NavLabelKey =
  | "home"
  | "profile"
  | "myComputers"
  | "hardware"
  | "campaigns";

export type NavItem = {
  id: string;
  labelKey: NavLabelKey;
  icon: IconName;
  href?: string;
  children?: readonly NavItem[];
};

export const primaryNavigation: readonly NavItem[] = [
  { id: "home", labelKey: "home", icon: "home", href: "/" },
  {
    id: "profile",
    labelKey: "profile",
    icon: "user",
    children: [
      { id: "my-computers", labelKey: "myComputers", icon: "desktop" }
    ]
  },
  { id: "hardware", labelKey: "hardware", icon: "cpu" },
  { id: "campaigns", labelKey: "campaigns", icon: "tag" }
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
  for (const item of items) {
    if (item.href && isActivePath(pathname, item.href)) {
      return item;
    }
    const child = item.children && findActiveItem(pathname, item.children);
    if (child) {
      return child;
    }
  }
  return undefined;
}
