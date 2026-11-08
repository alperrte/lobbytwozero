"use client";

import { useEffect, useRef, useState, type ReactNode } from "react";
import { useTranslations } from "next-intl";
import { ThemeTransitionLayer } from "@/shared/components/theme-transition/theme-transition-layer";
import { usePathname } from "@/shared/lib/i18n/navigation";
import { transitionBetween, welcomeVariantFor, type CardTransition } from "../services/auth-motion";
import { AuthCardSnake } from "./auth-card-snake";
import { AuthFooter } from "./auth-footer";
import { AuthNavbar } from "./auth-navbar";
import { TwistText } from "./twist-text";
import "./auth-stage.css";

/** Kartın giriş animasyonu (560 ms + 60 ms gecikme) bittikten hemen sonra ilk ışık turu başlar. */
const FIRST_LAP_DELAY_MS = 700;

const TRANSITION_CLASS: Record<CardTransition, string> = {
  none: "",
  "from-right": "auth-enter-from-right",
  "from-left": "auth-enter-from-left",
  unfold: "auth-enter-unfold",
  settle: "auth-enter-settle",
  fade: "auth-enter-fade"
};

type AuthStageProps = {
  children: ReactNode;
};

/**
 * `(auth)` grubunun kalıcı sahnesi: arkada tam ekran video (`AuthBackdrop`, layout'ta), üstte yüzen navbar, solda
 * karşılama tipografisi, sağda form kartı, altta footer. Sahne layout'ta durur; sayfa değişince YALNIZ kartın içeriği
 * (`children`) değişir. Böylece navbar, footer, kart yüzeyi ve video yeniden kurulmaz; içerik `pathname` anahtarıyla
 * yeni kurulur ve yönüne göre kısa bir giriş hareketi alır (`auth-motion.ts`). Form verisi anahtar sayesinde bir
 * ekrandan diğerine taşınmaz.
 *
 * Gezinme animasyonu beklenmez: yeni içerik hemen etkileşime açıktır, hareket yalnız görseldir. Sayfa değişiminde odak
 * yeni sayfanın başlığına (`h1`) taşınır; tıklanan bağlantı DOM'dan kalktığı için odak aksi halde kaybolurdu.
 */
export function AuthStage({ children }: AuthStageProps) {
  const t = useTranslations("Auth.shell");
  const pathname = usePathname();
  const [nav, setNav] = useState<{ path: string; previous: string | null }>({ path: pathname, previous: null });
  if (nav.path !== pathname) {
    setNav({ path: pathname, previous: nav.path });
  }
  const contentRef = useRef<HTMLDivElement>(null);
  const variant = welcomeVariantFor(pathname);
  const transition = transitionBetween(nav.previous, pathname);
  const firstScreen = nav.previous === null;

  useEffect(() => {
    if (nav.previous === null) {
      return;
    }
    const heading = contentRef.current?.querySelector<HTMLElement>("h1");
    if (heading) {
      heading.tabIndex = -1;
      heading.focus({ preventScroll: true });
    }
  }, [nav]);

  return (
    <div className="relative z-10 flex min-h-dvh flex-col text-text-primary">
      <AuthNavbar />

      <main className="flex flex-1 items-center px-4 py-8 sm:px-8 lg:px-12">
        <div className="mx-auto grid w-full max-w-[75rem] items-center gap-10 lg:grid-cols-[minmax(0,1fr)_28rem] xl:gap-20">
          <section
            key={variant}
            aria-label={t("welcomeLabel")}
            className="hidden text-white [text-shadow:0_2px_18px_rgb(0_0_0/0.5)] lg:block"
          >
            <p className="font-display text-5xl leading-[1.05] font-bold xl:text-6xl">
              <TwistText text={t(`${variant}.eyebrow`)} animate={firstScreen} />
            </p>
            <div className={firstScreen ? "auth-intro-left" : "auth-enter-fade"} style={{ "--auth-delay": "240ms" } as React.CSSProperties}>
              <p className="mt-6 font-display text-2xl leading-snug font-semibold text-white/95 xl:text-3xl">
                {t(`${variant}.headlineStart`)} <span className="text-accent">{t(`${variant}.headlineAccent`)}</span>
              </p>
              <p className="mt-5 max-w-md text-lg leading-relaxed text-white/85">{t(`${variant}.subline`)}</p>
            </div>
          </section>

          <div className="auth-intro-right relative w-full max-w-md justify-self-center rounded-3xl border border-border bg-surface shadow-popover lg:justify-self-end">
            <AuthCardSnake initialDelayMs={FIRST_LAP_DELAY_MS} />
            <div ref={contentRef} key={pathname} className={`p-6 sm:p-8 ${TRANSITION_CLASS[transition]}`}>
              {children}
            </div>
          </div>
        </div>
      </main>

      <AuthFooter />
      {/* Tema değişimi ana uygulamadaki sahneyle oynar (AppShell'deki katmanın aynısı). */}
      <ThemeTransitionLayer />
    </div>
  );
}
