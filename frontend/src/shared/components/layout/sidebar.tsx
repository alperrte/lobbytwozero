"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { primaryNavigation } from "@/shared/config/navigation";
import { useSidebarCollapsed } from "@/shared/hooks/use-sidebar-collapsed";
import { BrandLogo } from "./brand-logo";
import { NavButton, NavItem } from "./nav-item";

export const SIDEBAR_ID = "app-sidebar";

type SidebarProps = {
  open: boolean;
  onClose: () => void;
  onNavigate: () => void;
  onOpenSettings: () => void;
  closeButtonRef: React.Ref<HTMLButtonElement>;
};

/**
 * Tek kenar çubuğu: üstte ortalanmış LTZ logosu, ortada ana menü (Ana Sayfa, Donanımlar),
 * altta sabit kontrol bölümü (Ayarlar, daralt/genişlet). Masaüstünde sticky ve daraltılabilir,
 * küçük ekranda aynı bileşen drawer olarak açılır; ikinci bir mobil menü yoktur.
 * Logo satırı navbar ile aynı yatay eksene oturur (h-20: 12px boşluk + 56px navbar'ın ortası).
 */
export function Sidebar({
  open,
  onClose,
  onNavigate,
  onOpenSettings,
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
      className="invisible fixed inset-y-0 start-0 z-50 flex overscroll-contain w-72 max-w-[86vw] -translate-x-full flex-col border-e border-border bg-surface shadow-drawer transition-[translate,visibility] duration-200 ease-out delay-[0s,200ms] data-[open=true]:visible data-[open=true]:translate-x-0 data-[open=true]:transition-[translate] data-[open=true]:duration-200 lg:visible lg:sticky lg:top-0 lg:z-20 lg:h-dvh lg:w-72 lg:max-w-none lg:shrink-0 lg:translate-x-0 lg:shadow-none lg:transition-[width] lg:in-data-[sidebar=collapsed]:w-[4.5rem]"
    >
      <div className="relative flex h-20 shrink-0 items-center justify-center border-b border-border px-14 lg:px-4 lg:in-data-[sidebar=collapsed]:px-0">
        <Link
          href="/"
          aria-label={t("brand")}
          onClick={onNavigate}
          className="flex items-center justify-center rounded-control"
        >
          <BrandLogo
            wordmarkClassName="h-auto w-32 lg:in-data-[sidebar=collapsed]:hidden"
            markClassName="hidden h-auto w-10 lg:in-data-[sidebar=collapsed]:block"
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

      <nav
        aria-label={t("sidebarLabel")}
        className="flex-1 overflow-y-auto overscroll-contain px-3 py-4 lg:overflow-visible lg:in-data-[sidebar=collapsed]:px-2"
      >
        <ul className="space-y-1">
          {primaryNavigation.map((item) => (
            <NavItem
              key={item.id}
              item={item}
              pathname={pathname}
              onNavigate={onNavigate}
            />
          ))}
        </ul>
      </nav>

      <div className="shrink-0 border-t border-border px-3 py-3 lg:in-data-[sidebar=collapsed]:px-2">
        <ul className="space-y-1">
          <NavButton
            icon="settings"
            label={nav("settings")}
            onClick={onOpenSettings}
            aria-haspopup="dialog"
          />
          <NavButton
            icon="panel"
            label={collapsed ? t("expandSidebar") : t("collapseSidebar")}
            onClick={() => setCollapsed(!collapsed)}
            aria-expanded={!collapsed}
            aria-controls={SIDEBAR_ID}
            className="max-lg:hidden"
          />
        </ul>
      </div>
    </aside>
  );
}
