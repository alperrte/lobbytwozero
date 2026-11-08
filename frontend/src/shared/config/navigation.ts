import type { IconName } from "@/shared/components/ui/icons";
import type { AppPathname } from "@/shared/lib/i18n/routing";

/**
 * Global kenar çubuğu navigasyonu: 13 öğe, 3 grup (Genel, Sosyal, Donanım & Performans).
 * Ayarlar bağlantısı ve daralt/genişlet alt bölümdedir; ana listede yer almaz.
 *
 * - Menü, backend servislerine değil kullanıcının göreceği işlevlere göre kurulur; `service`
 *   yalnız hangi servisin ileride bu öğeyi besleyeceğini belgeler.
 * - Öğe, `href` taşıyorsa kullanılabilirdir. `href` olmayan öğe henüz yayınlanmamıştır:
 *   "Yakında" olarak, tıklanamaz biçimde gösterilir (kırık link ve sahte sayfa üretilmez).
 * - Bir özelliği açmak için yalnız ilgili öğeye gerçek rotasını `href` olarak yazmak yeterlidir:
 *   "Yakında" etiketi kalkar, bağlantı etkinleşir, aktif sayfa vurgusu otomatik çalışır.
 * - Menüye eklenmeyen servisler: auth, user, mail (mevcut akışlara gömülü), notification
 *   (navbar bildirimleri), admin (yalnız yetkililer için ayrı yüzey).
 * - Donanım Detayı katalogdan açılır; ayrı menü öğesi değildir.
 */
export type NavLabelKey =
  | "home"
  | "games"
  | "library"
  | "reviews"
  | "news"
  | "forYou"
  | "community"
  | "friends"
  | "messages"
  | "findTeam"
  | "events"
  | "hardware"
  | "performance"
  | "settings";

export type NavGroupId = "general" | "social" | "hardware";

export type NavItem = {
  id: string;
  labelKey: NavLabelKey;
  icon: IconName;
  /** Yalnız gerçekten var olan rota (dahili yol, `routing.ts` anahtarı). Yoksa öğe "Yakında" durumundadır. */
  href?: AppPathname;
  /** Öğeyi ileride besleyecek backend servisi (belgeleme amaçlıdır; arayüzde gösterilmez). */
  service?: string;
};

export type NavGroup = {
  id: NavGroupId;
  labelKey: NavGroupId;
  items: readonly NavItem[];
};

export const navigationGroups: readonly NavGroup[] = [
  {
    id: "general",
    labelKey: "general",
    items: [
      { id: "home", labelKey: "home", icon: "home", href: "/" },
      { id: "games", labelKey: "games", icon: "gamepad", service: "game-service", href: "/games" },
      { id: "library", labelKey: "library", icon: "library", service: "library-service" },
      { id: "reviews", labelKey: "reviews", icon: "star", service: "review-service" },
      { id: "news", labelKey: "news", icon: "newspaper", service: "content-service" },
      {
        id: "forYou",
        labelKey: "forYou",
        icon: "sparkles",
        service: "recommendation-service"
      }
    ]
  },
  {
    id: "social",
    labelKey: "social",
    items: [
      { id: "community", labelKey: "community", icon: "users", service: "community-service" },
      {
        id: "friends",
        labelKey: "friends",
        icon: "userCheck",
        service: "social-service",
        href: "/friends"
      },
      {
        id: "messages",
        labelKey: "messages",
        icon: "messages",
        service: "messaging-service"
      },
      { id: "findTeam", labelKey: "findTeam", icon: "usersRound", service: "lfp-service" },
      { id: "events", labelKey: "events", icon: "calendar", service: "event-service" }
    ]
  },
  {
    id: "hardware",
    labelKey: "hardware",
    items: [
      { id: "hardware", labelKey: "hardware", icon: "cpu", service: "hardware-service" },
      {
        id: "performance",
        labelKey: "performance",
        icon: "chart",
        service: "performance-service"
      }
    ]
  }
];

/** Tüm gezinme öğeleri (grup sırasıyla). */
export const primaryNavigation: readonly NavItem[] = navigationGroups.flatMap(
  (group) => group.items
);

/** Alt bölüm: Ayarlar ayrı bir sayfadır (diyalog değil); ana gezinme listesinde yer almaz. */
export const settingsNavigation: NavItem = {
  id: "settings",
  labelKey: "settings",
  icon: "settings",
  href: "/settings"
};

/** Öğe yalnız gerçek bir rotaya sahipse kullanılabilir; aksi hâlde "Yakında" gösterilir. */
export function isNavItemAvailable(item: NavItem): item is NavItem & { href: AppPathname } {
  return item.href !== undefined;
}

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
