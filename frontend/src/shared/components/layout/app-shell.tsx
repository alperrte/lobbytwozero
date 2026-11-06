"use client";

import { useEffect, useRef, useState, type ReactNode } from "react";
import { usePathname } from "next/navigation";
import { useTranslations } from "next-intl";
import { ThemeTransitionLayer } from "@/shared/components/theme-transition/theme-transition-layer";
import { Navbar } from "./navbar";
import { SearchDialog } from "./search-dialog";
import { SettingsDialog } from "./settings-dialog";
import { Sidebar } from "./sidebar";

type AppShellProps = {
  children: ReactNode;
  footer: ReactNode;
};

const DESKTOP_QUERY = "(min-width: 1024px)";

/**
 * Tek ortak uygulama kabuğu: skip link, kenar çubuğu (masaüstünde daraltılabilir,
 * küçük ekranda drawer), yüzen navbar, ana içerik ve footer. Sayfa içeriği ve footer
 * Server Component olarak dışarıdan gelir; yalnız etkileşim burada Client'tır.
 * Arama ve ayarlar diyalogları da burada durur; Ctrl/Cmd+K genel kısayoldur.
 */
export function AppShell({ children, footer }: AppShellProps) {
  const t = useTranslations("Shell");
  const pathname = usePathname();
  // Drawer hangi rotada açıldıysa o rotada açık sayılır; rota değişince kendiliğinden kapanır.
  const [openedAt, setOpenedAt] = useState<string | null>(null);
  const menuOpen = openedAt === pathname;
  const [searchOpen, setSearchOpen] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const menuButtonRef = useRef<HTMLButtonElement>(null);
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  // Odak, ana alandan `inert` kalktıktan sonra (efekt temizliğinde) menü düğmesine döner;
  // kapanış anında düğme hâlâ inert olduğundan doğrudan focus() çağrısı sessizce başarısız olur.
  const restoreFocusRef = useRef(false);
  const focusMenuButtonOnSettingsCloseRef = useRef(false);

  function closeMenu(restoreFocus: boolean) {
    restoreFocusRef.current = restoreFocus;
    setOpenedAt(null);
  }

  function openSettings() {
    // Mobilde drawer ayarlar açılırken kapanır; odak ayarlar kapanınca menü düğmesine döner.
    focusMenuButtonOnSettingsCloseRef.current = menuOpen;
    if (menuOpen) {
      closeMenu(false);
    }
    setSettingsOpen(true);
  }

  function closeSettings() {
    setSettingsOpen(false);
    if (focusMenuButtonOnSettingsCloseRef.current) {
      focusMenuButtonOnSettingsCloseRef.current = false;
      window.requestAnimationFrame(() => menuButtonRef.current?.focus());
    }
  }

  useEffect(() => {
    if (!menuOpen) {
      return;
    }

    const root = document.documentElement;
    const menuButton = menuButtonRef.current;
    const media = window.matchMedia(DESKTOP_QUERY);
    const focusFrame = window.requestAnimationFrame(() =>
      closeButtonRef.current?.focus()
    );

    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        restoreFocusRef.current = true;
        setOpenedAt(null);
      }
    }
    function onMediaChange(event: MediaQueryListEvent) {
      if (event.matches) {
        setOpenedAt(null);
      }
    }

    root.style.overflow = "hidden";
    document.addEventListener("keydown", onKeyDown);
    media.addEventListener("change", onMediaChange);
    return () => {
      window.cancelAnimationFrame(focusFrame);
      root.style.overflow = "";
      document.removeEventListener("keydown", onKeyDown);
      media.removeEventListener("change", onMediaChange);
      if (restoreFocusRef.current) {
        restoreFocusRef.current = false;
        menuButton?.focus();
      }
    };
  }, [menuOpen]);

  // Ctrl/Cmd+K: aramayı aç/kapat. Ayarlar ya da mobil menü açıkken arka plana karışmaz.
  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (event.key.toLowerCase() !== "k" || !(event.ctrlKey || event.metaKey)) {
        return;
      }
      if (event.altKey || event.shiftKey || settingsOpen || menuOpen) {
        return;
      }
      event.preventDefault();
      setSearchOpen((open) => !open);
    }

    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [settingsOpen, menuOpen]);

  return (
    <>
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:fixed focus:start-3 focus:top-3 focus:z-[60] focus:rounded-control focus:bg-primary focus:px-4 focus:py-2 focus:font-display focus:text-sm focus:font-semibold focus:text-primary-foreground"
      >
        {t("skipToContent")}
      </a>

      <div className="flex min-h-dvh">
        <Sidebar
          open={menuOpen}
          onClose={() => closeMenu(true)}
          onNavigate={() => closeMenu(false)}
          onOpenSettings={openSettings}
          closeButtonRef={closeButtonRef}
        />

        <div
          aria-hidden="true"
          data-open={menuOpen}
          onClick={() => closeMenu(true)}
          className="pointer-events-none fixed inset-0 z-40 bg-scrim opacity-0 transition-opacity duration-200 data-[open=true]:pointer-events-auto data-[open=true]:opacity-100 lg:hidden"
        />

        <div inert={menuOpen} className="flex min-w-0 flex-1 flex-col">
          <Navbar
            menuOpen={menuOpen}
            onOpenMenu={() => setOpenedAt(pathname)}
            onOpenSearch={() => setSearchOpen(true)}
            menuButtonRef={menuButtonRef}
          />
          <main
            id="main-content"
            tabIndex={-1}
            className="flex-1 px-4 pb-8 pt-6 focus:outline-none sm:px-6 sm:pb-10 lg:px-10"
          >
            {children}
          </main>
          {footer}
        </div>
      </div>

      <SearchDialog open={searchOpen} onClose={() => setSearchOpen(false)} />
      <SettingsDialog open={settingsOpen} onClose={closeSettings} />
      <ThemeTransitionLayer />
    </>
  );
}
