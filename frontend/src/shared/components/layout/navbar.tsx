"use client";

import { usePathname } from "next/navigation";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { ThemeSwitch } from "@/shared/components/ui/theme-switch";
import { iconButtonClass } from "@/shared/components/ui/styles";
import { findPage } from "@/shared/config/pages";
import { useModifierKey } from "@/shared/hooks/use-modifier-key";
import { useNavbarAutoHide } from "@/shared/hooks/use-navbar-autohide";
import { useNavbarMode } from "@/shared/hooks/use-navbar-mode";
import { useShellNotifications } from "@/shared/hooks/use-shell-notifications";
import { useShellUser } from "@/shared/hooks/use-shell-user";
import { useSidebarCollapsed } from "@/shared/hooks/use-sidebar-collapsed";
import { cn } from "@/shared/utils/cn";
import { LanguageMenu } from "./language-menu";
import { NotificationMenu } from "./notification-menu";
import { ProfileMenu } from "./profile-menu";
import { SIDEBAR_ID } from "./sidebar";

type NavbarProps = {
  menuOpen: boolean;
  onOpenMenu: () => void;
  onOpenSearch: () => void;
  menuButtonRef: React.Ref<HTMLButtonElement>;
};

function PageName() {
  const t = useTranslations("Shell");
  const pages = useTranslations("Pages");
  const footer = useTranslations("Footer");
  const pathname = usePathname();
  const page = findPage(pathname);

  if (!page) {
    return (
      <span translate="no" className="truncate font-display text-sm font-medium">
        {t("brand")}
      </span>
    );
  }

  return (
    <nav aria-label={t("breadcrumb")} className="min-w-0">
      <ol className="flex items-center gap-1.5 font-display text-sm font-medium">
        {page.section === "legal" ? (
          <>
            <li className="hidden text-text-secondary lg:block">{footer("legal")}</li>
            <li aria-hidden="true" className="hidden text-text-secondary lg:block">
              /
            </li>
          </>
        ) : null}
        <li aria-current="page" className="truncate">
          {pages(`${page.id}.title`)}
        </li>
      </ol>
    </nav>
  );
}

/**
 * Yüzen navbar: ekran kenarlarından ayrık, yuvarlak köşeli, ince kenarlıklı kart.
 * Otomatik gizleme modunda aşağı kaydırınca saklanır, yukarı kaydırınca ya da odak içine
 * girince geri gelir; açık bir menü varken de görünür kalır. Sabit modda her zaman görünür.
 */
export function Navbar({
  menuOpen,
  onOpenMenu,
  onOpenSearch,
  menuButtonRef
}: NavbarProps) {
  const t = useTranslations("Shell");
  const search = useTranslations("Navbar.search");
  const [mode] = useNavbarMode();
  const hidden = useNavbarAutoHide(mode === "auto");
  const [collapsed, setCollapsed] = useSidebarCollapsed();
  const modifier = useModifierKey();
  const user = useShellUser();
  const notifications = useShellNotifications();

  return (
    <div
      data-hidden={hidden}
      className={cn(
        "pointer-events-none sticky top-0 z-30 px-3 pt-3 transition-[translate] duration-300 ease-out sm:px-5",
        "data-[hidden=true]:-translate-y-[calc(100%+0.25rem)]",
        "focus-within:translate-y-0 has-[[aria-expanded=true]]:translate-y-0"
      )}
    >
      <header
        aria-label={t("navbarLabel")}
        className="edge-light pointer-events-auto relative flex h-header items-center gap-1.5 rounded-float border border-border bg-surface px-2 shadow-float sm:gap-2 sm:px-3"
      >
        <button
          ref={menuButtonRef}
          type="button"
          onClick={onOpenMenu}
          aria-label={t("openMenu")}
          aria-expanded={menuOpen}
          aria-controls={SIDEBAR_ID}
          className={cn(iconButtonClass, "lg:hidden")}
        >
          <Icon name="menu" />
        </button>

        <button
          type="button"
          onClick={() => setCollapsed(!collapsed)}
          aria-expanded={!collapsed}
          aria-controls={SIDEBAR_ID}
          className={cn(iconButtonClass, "max-lg:hidden")}
        >
          <Icon name="panel" />
          <span className="sr-only">
            {collapsed ? t("expandSidebar") : t("collapseSidebar")}
          </span>
        </button>

        <div className="hidden min-w-0 sm:block lg:max-w-56">
          <PageName />
        </div>

        <div className="hidden min-w-0 flex-1 justify-center px-2 md:flex">
          <button
            type="button"
            onClick={onOpenSearch}
            aria-haspopup="dialog"
            aria-keyshortcuts="Control+K Meta+K"
            className="flex h-10 w-full max-w-md items-center gap-2.5 rounded-control border border-border bg-surface-elevated px-3 text-sm text-text-secondary transition-colors hover:border-control-border hover:text-text-primary"
          >
            <Icon name="search" className="size-[18px] shrink-0" />
            <span className="flex-1 truncate text-start">{search("placeholder")}</span>
            <kbd
              aria-hidden="true"
              className="rounded-control border border-border bg-surface px-1.5 py-0.5 font-sans text-xs font-medium leading-none text-text-secondary"
            >
              {modifier} K
            </kbd>
          </button>
        </div>

        <div className="ms-auto flex shrink-0 items-center gap-0.5 sm:gap-1.5 md:ms-0">
          <button
            type="button"
            onClick={onOpenSearch}
            aria-haspopup="dialog"
            aria-label={search("open")}
            className={cn(iconButtonClass, "md:hidden")}
          >
            <Icon name="search" />
          </button>
          <NotificationMenu notifications={notifications} />
          <LanguageMenu />
          <ThemeSwitch />
          <ProfileMenu user={user} />
        </div>
      </header>
    </div>
  );
}
