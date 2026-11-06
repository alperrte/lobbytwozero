"use client";

import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { ThemeSwitch } from "@/shared/components/ui/theme-switch";
import { iconButtonClass } from "@/shared/components/ui/styles";
import { useNavbarAutoHide } from "@/shared/hooks/use-navbar-autohide";
import { useNavbarMode } from "@/shared/hooks/use-navbar-mode";
import { useShellNotifications } from "@/shared/hooks/use-shell-notifications";
import { useShellUser } from "@/shared/hooks/use-shell-user";
import { cn } from "@/shared/utils/cn";
import { EdgeSnake } from "./edge-snake";
import { LanguageMenu } from "./language-menu";
import { NavbarSearch } from "./navbar-search";
import { NotificationMenu } from "./notification-menu";
import { ProfileMenu } from "./profile-menu";
import { SIDEBAR_ID } from "./sidebar";

type NavbarProps = {
  menuOpen: boolean;
  onOpenMenu: () => void;
  menuButtonRef: React.Ref<HTMLButtonElement>;
};

/**
 * Yüzen "komut çubuğu": yalnız arama, bildirim, dil, tema ve profil bulunur (sayfa adı yok).
 * İçerik alanında ortalanır (en çok 960px), kenarlardan ayrıktır, 18px köşe yarıçaplı düz bir
 * yüzeydir. Çerçevesi boyunca tek bir kırmızı→pembe ışık izi (EdgeSnake) saat yönünde döner.
 * Otomatik gizleme modunda aşağı kaydırınca saklanır, yukarı
 * kaydırınca ya da odak/açık menü varken geri gelir. Sabit modda her zaman görünür.
 */
export function Navbar({ menuOpen, onOpenMenu, menuButtonRef }: NavbarProps) {
  const t = useTranslations("Shell");
  const [mode] = useNavbarMode();
  const hidden = useNavbarAutoHide(mode === "auto");
  const user = useShellUser();
  const notifications = useShellNotifications();

  return (
    <div
      data-hidden={hidden}
      className={cn(
        "pointer-events-none sticky top-0 z-30 flex justify-center px-3 pt-3 transition-[translate] duration-300 ease-out sm:px-5",
        "data-[hidden=true]:-translate-y-[calc(100%+0.25rem)]",
        "focus-within:translate-y-0 has-[[aria-expanded=true]]:translate-y-0"
      )}
    >
      <header
        aria-label={t("navbarLabel")}
        className="pointer-events-auto relative flex h-header w-full max-w-[60rem] items-center gap-2 rounded-navbar border border-border bg-surface px-3 shadow-float sm:gap-4 sm:px-4"
      >
        <EdgeSnake />
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

        <NavbarSearch />

        <div className="ms-auto flex shrink-0 items-center gap-0.5 sm:gap-1.5 md:ms-0">
          <NotificationMenu notifications={notifications} />
          <LanguageMenu />
          <ThemeSwitch />
          <ProfileMenu user={user} />
        </div>
      </header>
    </div>
  );
}
