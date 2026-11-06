"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { navigationGroups, settingsNavigation } from "@/shared/config/navigation";
import { useSidebarCollapsed } from "@/shared/hooks/use-sidebar-collapsed";
import { cn } from "@/shared/utils/cn";
import { BrandLogo } from "./brand-logo";
import { NavButton, NavItem } from "./nav-item";

export const SIDEBAR_ID = "app-sidebar";

type SidebarProps = {
  open: boolean;
  onClose: () => void;
  onNavigate: () => void;
  closeButtonRef: React.Ref<HTMLButtonElement>;
};

/**
 * Yüzen oyun gezinme rayı. Üç bölgeden oluşur: sabit logo alanı, yalnız kendisi kayan menü
 * (Genel, Sosyal, Donanım & Performans) ve sabit alt kontroller (Ayarlar, daralt/genişlet).
 *
 * - Masaüstünde (lg+) kenarlardan ayrık, yuvarlatılmış bir panel olarak sayfayla birlikte sabit kalır;
 *   dış `aside` yalnız yer ayırır (panel genişliği + boşluk), böylece içerik genişliğe uyum sağlar.
 *   Daraltılmış hâlde (72px) yalnız ikon logo ve ikonlar görünür.
 * - Küçük ekranda aynı bileşen drawer olarak açılır; ikinci bir mobil menü yoktur.
 * - Logonun yanında yazı yoktur: geniş hâlde yatay logo, dar hâlde ikon logo.
 * - Daraltma durumu <html data-sidebar> üzerindedir; stil `in-data-[sidebar=collapsed]` ile okunur.
 */
export function Sidebar({
  open,
  onClose,
  onNavigate,
  closeButtonRef
}: SidebarProps) {
  const t = useTranslations("Shell");
  const nav = useTranslations("Navigation");
  const pathname = usePathname();
  const [collapsed, setCollapsed] = useSidebarCollapsed();

  return (
    <aside
      id={SIDEBAR_ID}
      data-open={open}
      className="invisible fixed inset-y-0 start-0 z-50 w-72 max-w-[86vw] -translate-x-full transition-[translate,visibility] duration-300 ease-[cubic-bezier(0.32,0.72,0,1)] delay-[0s,300ms] data-[open=true]:visible data-[open=true]:translate-x-0 data-[open=true]:transition-[translate] data-[open=true]:duration-300 data-[open=true]:delay-0 motion-reduce:duration-0 lg:visible lg:sticky lg:top-0 lg:z-20 lg:h-dvh lg:w-[17.25rem] lg:max-w-none lg:shrink-0 lg:translate-x-0 lg:p-3 lg:pe-2 lg:transition-[width] lg:duration-[220ms] lg:ease-[cubic-bezier(0.32,0.72,0,1)] lg:delay-0 lg:in-data-[sidebar=collapsed]:w-[5.75rem]"
    >
      <div
        data-rail
        className="flex h-full min-h-0 flex-col overflow-hidden bg-surface max-lg:border-e max-lg:border-border max-lg:shadow-drawer lg:rounded-2xl lg:border lg:border-border lg:shadow-float"
      >
        {/* 1) Sabit logo alanı */}
        <div className="relative flex h-20 shrink-0 items-center justify-center border-b border-border/70 px-14 lg:px-4 lg:in-data-[sidebar=collapsed]:px-0">
          <Link
            href="/"
            aria-label={t("brand")}
            onClick={onNavigate}
            className="flex items-center justify-center rounded-control"
          >
            <BrandLogo
              wordmarkClassName="h-auto w-[8.5rem] object-contain lg:in-data-[sidebar=collapsed]:hidden"
              markClassName="hidden h-auto w-11 object-contain lg:in-data-[sidebar=collapsed]:block"
            />
          </Link>
          <button
            ref={closeButtonRef}
            type="button"
            onClick={onClose}
            aria-label={t("closeMenu")}
            className="absolute end-3 top-1/2 inline-flex size-10 -translate-y-1/2 items-center justify-center rounded-control text-text-secondary transition-colors hover:bg-surface-elevated hover:text-text-primary lg:hidden"
          >
            <Icon name="close" />
          </button>
        </div>

        {/* 2) Yalnız burası kayar */}
        <nav
          aria-label={t("sidebarLabel")}
          className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-3 py-2 [scrollbar-color:var(--ltz-border)_transparent] [scrollbar-width:thin] lg:in-data-[sidebar=collapsed]:px-2"
        >
          {navigationGroups.map((group, index) => {
            const headingId = `${SIDEBAR_ID}-group-${group.id}`;
            return (
              <div
                key={group.id}
                className={cn(
                  index > 0 &&
                    "mt-2 lg:in-data-[sidebar=collapsed]:border-t lg:in-data-[sidebar=collapsed]:border-border/70 lg:in-data-[sidebar=collapsed]:pt-2"
                )}
              >
                <p
                  id={headingId}
                  className="px-3 pb-1.5 pt-3 font-display text-[0.6875rem] font-semibold uppercase leading-none tracking-[0.1em] text-text-secondary lg:in-data-[sidebar=collapsed]:sr-only"
                >
                  {nav(`groups.${group.labelKey}`)}
                </p>
                <ul aria-labelledby={headingId} className="space-y-0.5">
                  {group.items.map((item) => (
                    <NavItem
                      key={item.id}
                      item={item}
                      pathname={pathname}
                      onNavigate={onNavigate}
                    />
                  ))}
                </ul>
              </div>
            );
          })}
        </nav>

        {/* 3) Sabit alt kontroller */}
        <div className="shrink-0 border-t border-border/70 p-3 lg:in-data-[sidebar=collapsed]:px-2">
          <ul className="space-y-0.5">
            <NavItem
              item={settingsNavigation}
              pathname={pathname}
              onNavigate={onNavigate}
            />
            <NavButton
              icon={collapsed ? "panelLeftOpen" : "panelLeftClose"}
              label={collapsed ? t("expandSidebar") : t("collapseSidebar")}
              onClick={() => setCollapsed(!collapsed)}
              aria-expanded={!collapsed}
              aria-controls={SIDEBAR_ID}
              className="max-lg:hidden"
            />
          </ul>
        </div>
      </div>
    </aside>
  );
}
