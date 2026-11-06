"use client";

import { useEffect, useRef, useState, type ReactNode } from "react";
import { useTranslations } from "next-intl";
import { usePathname } from "@/shared/lib/i18n/navigation";
import { ThemeTransitionLayer } from "@/shared/components/theme-transition/theme-transition-layer";
import { Navbar } from "./navbar";
import { Sidebar } from "./sidebar";

type AppShellProps = {
  children: ReactNode;
  footer: ReactNode;
};

const DESKTOP_QUERY = "(min-width: 1024px)";

/**
 * Tek ortak uygulama kabuğu: skip link, kenar çubuğu (masaüstünde daraltılabilir,
 * küçük ekranda drawer), yüzen navbar, ana içerik ve footer. Footer, sidebar ve içeriğin oluşturduğu
 * üst satırın altında, tam genişlikte ve belge akışındadır. Sayfa içeriği ve footer
 * Server Component olarak dışarıdan gelir; yalnız etkileşim burada Client'tır.
 * Arama navbar'a gömülüdür; Ayarlar ayrı bir sayfadır (diyalog yok). İçerik, navbar ile aynı
 * yan boşluklara hizalıdır ve ortalanmış dar bir sütuna sıkıştırılmaz.
 */
export function AppShell({ children, footer }: AppShellProps) {
  const t = useTranslations("Shell");
  const pathname = usePathname();
  // Drawer hangi rotada açıldıysa o rotada açık sayılır; rota değişince kendiliğinden kapanır.
  const [openedAt, setOpenedAt] = useState<string | null>(null);
  const menuOpen = openedAt === pathname;
  const menuButtonRef = useRef<HTMLButtonElement>(null);
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  // Odak, ana alandan `inert` kalktıktan sonra (efekt temizliğinde) menü düğmesine döner;
  // kapanış anında düğme hâlâ inert olduğundan doğrudan focus() çağrısı sessizce başarısız olur.
  const restoreFocusRef = useRef(false);

  function closeMenu(restoreFocus: boolean) {
    restoreFocusRef.current = restoreFocus;
    setOpenedAt(null);
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

  return (
    <>
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:fixed focus:start-3 focus:top-3 focus:z-[60] focus:rounded-control focus:bg-primary focus:px-4 focus:py-2 focus:font-display focus:text-sm focus:font-semibold focus:text-primary-foreground"
      >
        {t("skipToContent")}
      </a>

      <div>
        {/* Bölge A — uygulama kabuğu: sidebar + (navbar, içerik). En az viewport yüksekliğindedir ve içerik
            uzadıkça büyür (yükseklik KESİLMEZ). Sidebar paneli bu satırda sticky durur. */}
        <div className="flex min-h-dvh">
          <Sidebar
            open={menuOpen}
            onClose={() => closeMenu(true)}
            onNavigate={() => closeMenu(false)}
            closeButtonRef={closeButtonRef}
          />

          <div
            aria-hidden="true"
            data-open={menuOpen}
            onClick={() => closeMenu(true)}
            className="pointer-events-none fixed inset-0 z-40 bg-scrim opacity-0 transition-opacity duration-300 ease-out motion-reduce:duration-0 data-[open=true]:pointer-events-auto data-[open=true]:opacity-100 lg:hidden"
          />

          <div inert={menuOpen} className="flex min-w-0 flex-1 flex-col">
            <Navbar
              menuOpen={menuOpen}
              onOpenMenu={() => setOpenedAt(pathname)}
              menuButtonRef={menuButtonRef}
            />
            <main
              id="main-content"
              tabIndex={-1}
              className="flex-1 px-3 pb-8 pt-6 focus:outline-none sm:px-5 sm:pb-10"
            >
              {children}
            </main>
          </div>
        </div>

        {/* Bölge B — footer: kabuğun tamamen altında, tam genişlikte, normal akışta; altında ek boşluk yok. */}
        <div inert={menuOpen}>{footer}</div>
      </div>

      <ThemeTransitionLayer />
    </>
  );
}
