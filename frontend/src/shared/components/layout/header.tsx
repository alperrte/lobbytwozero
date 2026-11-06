"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { LanguageSwitcher } from "@/shared/components/ui/language-switcher";
import { ThemeToggle } from "@/shared/components/ui/theme-toggle";
import { findActiveItem } from "@/shared/config/navigation";
import { useSidebarCollapsed } from "@/shared/hooks/use-sidebar-collapsed";
import { BrandLogo } from "./brand-logo";
import { SIDEBAR_ID } from "./sidebar";

type HeaderProps = {
  menuOpen: boolean;
  onOpenMenu: () => void;
  menuButtonRef: React.Ref<HTMLButtonElement>;
};

const iconButton =
  "size-10 shrink-0 items-center justify-center rounded-control text-text-secondary transition-colors hover:bg-surface-elevated hover:text-text-primary";

export function Header({ menuOpen, onOpenMenu, menuButtonRef }: HeaderProps) {
  const t = useTranslations("Shell");
  const nav = useTranslations("Navigation");
  const pathname = usePathname();
  const [collapsed, setCollapsed] = useSidebarCollapsed();
  const active = findActiveItem(pathname);

  return (
    <header className="edge-light sticky top-0 z-30 flex h-header items-center gap-2 border-b border-border bg-surface px-3 sm:px-5">
      <button
        ref={menuButtonRef}
        type="button"
        onClick={onOpenMenu}
        aria-label={t("openMenu")}
        aria-expanded={menuOpen}
        aria-controls={SIDEBAR_ID}
        className={`${iconButton} flex lg:hidden`}
      >
        <Icon name="menu" />
      </button>

      <Link
        href="/"
        aria-label={t("brand")}
        className="flex items-center rounded-control lg:hidden"
      >
        <BrandLogo
          wordmarkClassName="hidden h-auto w-24 sm:block"
          markClassName="h-auto w-9 sm:hidden"
        />
      </Link>

      <button
        type="button"
        onClick={() => setCollapsed(!collapsed)}
        aria-expanded={!collapsed}
        aria-controls={SIDEBAR_ID}
        className={`${iconButton} hidden lg:flex`}
      >
        <Icon name="panel" />
        <span className="sr-only">
          {collapsed ? t("expandSidebar") : t("collapseSidebar")}
        </span>
      </button>

      <span
        translate={active ? undefined : "no"}
        className="hidden truncate font-display text-sm font-medium text-text-secondary lg:block"
      >
        {active ? nav(active.labelKey) : t("brand")}
      </span>

      <div className="ms-auto flex items-center gap-2">
        <LanguageSwitcher />
        <ThemeToggle />
      </div>
    </header>
  );
}
