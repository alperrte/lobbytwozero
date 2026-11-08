"use client";

import { useEffect, useLayoutEffect, useRef, useState, type ReactNode } from "react";
import { useTranslations } from "next-intl";
import { ThemeTransitionLayer } from "@/shared/components/theme-transition/theme-transition-layer";
import { usePathname } from "@/shared/lib/i18n/navigation";
import { exitFor, transitionBetween, welcomeVariantFor, type CardTransition } from "../services/auth-motion";
import { AuthCardSnake } from "./auth-card-snake";
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

/** Çıkış animasyonu 350 ms sürer; eski içerik anlık görüntüsü bundan sonra (güvenlik payıyla) kaldırılır. */
const LEAVE_REMOVE_MS = 450;
/** Kart yüksekliğinin yeni içeriğe yumuşakça uyum süresi. */
const HEIGHT_MS = 480;
const MAX_LEAVING = 2;

function prefersReducedMotion(): boolean {
  return (
    typeof window !== "undefined" &&
    typeof window.matchMedia === "function" &&
    window.matchMedia("(prefers-reduced-motion: reduce)").matches
  );
}

/**
 * Geçerli kart içeriğinin, çıkış animasyonu için kullanılacak etkileşimsiz DOM kopyası. Kimlik/ad/`for` nitelikleri
 * silinir (kopya canlı formla aynı kimliği taşımasın), yazılmış metin korunur; PAROLA alanlarının değeri kopyalanmaz.
 */
function snapshotOf(entry: HTMLElement): HTMLElement {
  const clone = entry.cloneNode(true) as HTMLElement;
  const sources = entry.querySelectorAll<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>("input, textarea, select");
  const copies = clone.querySelectorAll<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>("input, textarea, select");
  sources.forEach((source, index) => {
    const copy = copies[index];
    if (copy) {
      // cloneNode girdi değerini de taşıyabilir; parola açıkça temizlenir.
      copy.value = source instanceof HTMLInputElement && source.type === "password" ? "" : source.value;
    }
  });
  for (const node of clone.querySelectorAll("[id], [name], [for], [autofocus]")) {
    node.removeAttribute("id");
    node.removeAttribute("name");
    node.removeAttribute("for");
    node.removeAttribute("autofocus");
  }
  clone.removeAttribute("data-auth-entry");
  clone.className = "";
  return clone;
}

type AuthStageProps = {
  children: ReactNode;
  /** Ana uygulamadaki footer (sunucu bileşeni; layout verir). İlk ekranın ALTINDADIR: kaydırınca görünür. */
  footer?: ReactNode;
};

/**
 * `(auth)` grubunun kalıcı sahnesi: arkada tam ekran video (`AuthBackdrop`, layout'ta), üstte yüzen navbar, solda
 * karşılama tipografisi, sağda form kartı, altta footer. Sahne layout'ta durur; sayfa değişince YALNIZ kartın içeriği
 * (`children`) değişir. Böylece navbar, footer, kart yüzeyi ve video yeniden kurulmaz; içerik `pathname` anahtarıyla
 * yeni kurulur ve yönüne göre kısa bir giriş hareketi alır (`auth-motion.ts`). Form verisi anahtar sayesinde bir
 * ekrandan diğerine taşınmaz.
 *
 * Çıkış + giriş: App Router yeni içeriği hemen verir (ve `children` her zaman GEÇERLİ rotayı çizen bir router öğesidir,
 * eski sayfayı yeniden çizmek mümkün değildir). Bu yüzden eski kartın DOM görüntüsü her etkileşimde ve her işlemede
 * kopyalanır; sayfa değişince kopya "çıkan katman" olarak ≈240 ms eklenir: `inert` + `aria-hidden` (odak ve gönderim
 * kabul etmez), mutlak konumlu (düzeni etkilemez), yönüne göre soluklaşarak kayar ve kaldırılır. Kart yüksekliği eski
 * değerden yenisine Web Animations ile yumuşakça geçer; sıçrama olmaz. Gezinme animasyon beklenmeden yapılır (yeni
 * içerik hemen etkileşime açıktır); hızlı gezinmede en fazla iki çıkan katman tutulur. "Hareketi azalt"ta çıkan
 * katman hiç oluşturulmaz.
 * Sayfa değişiminde odak yeni sayfanın başlığına (`h1`) taşınır.
 */
export function AuthStage({ children, footer }: AuthStageProps) {
  const t = useTranslations("Auth.shell");
  const pathname = usePathname();
  const [nav, setNav] = useState<{ path: string; previous: string | null }>({ path: pathname, previous: null });
  const contentRef = useRef<HTMLDivElement>(null);
  const ghostsRef = useRef<HTMLDivElement>(null);
  const snapshotRef = useRef<HTMLElement | null>(null);
  const heightRef = useRef(0);
  const variant = welcomeVariantFor(pathname);
  const transition = transitionBetween(nav.path === pathname ? nav.previous : nav.path, pathname);
  const firstScreen = nav.previous === null && nav.path === pathname;

  if (nav.path !== pathname) {
    setNav({ path: pathname, previous: nav.path });
  }

  /** Geçerli kart içeriğinin güncel kopyasını alır (etkileşimlerde ve her işlemden sonra). */
  function capture() {
    const entry = contentRef.current?.querySelector<HTMLElement>('[data-auth-entry="current"]');
    if (entry) {
      snapshotRef.current = snapshotOf(entry);
    }
  }

  // Sayfa değişince: önceki kopya çıkan katman olarak eklenir (boyamadan önce; yeni içerikle aynı karede görünür).
  useLayoutEffect(() => {
    const layer = ghostsRef.current;
    const snapshot = snapshotRef.current;
    if (nav.previous === null || !layer || !snapshot || prefersReducedMotion()) {
      return;
    }
    const ghost = document.createElement("div");
    ghost.dataset.authEntry = "leaving";
    ghost.setAttribute("aria-hidden", "true");
    ghost.setAttribute("inert", "");
    ghost.className = `auth-leaving auth-leaving--${exitFor(transitionBetween(nav.previous, nav.path))} p-6 sm:p-8`;
    ghost.append(...Array.from(snapshot.childNodes));
    layer.append(ghost);
    while (layer.children.length > MAX_LEAVING) {
      layer.firstElementChild?.remove();
    }
    window.setTimeout(() => ghost.remove(), LEAVE_REMOVE_MS);
  }, [nav]);

  // Kopya: ilk işlemede, her sahne işlemesinden sonra ve kullanıcı etkileşimlerinde (yazma, tıklama, odak) güncellenir.
  useEffect(() => {
    capture();
  });

  useEffect(() => {
    const container = contentRef.current;
    if (!container) {
      return;
    }
    const events = ["pointerdown", "keydown", "input", "change", "focusin"] as const;
    const onEvent = () => capture();
    for (const name of events) {
      container.addEventListener(name, onEvent, true);
    }
    return () => {
      for (const name of events) {
        container.removeEventListener(name, onEvent, true);
      }
    };
  }, []);

  // Kart yüksekliği: değişimi izler; sayfa değişince eski değerden yenisine animasyon verir.
  useLayoutEffect(() => {
    const container = contentRef.current;
    if (!container) {
      return;
    }
    const to = container.offsetHeight;
    const from = heightRef.current;
    heightRef.current = to;
    if (nav.previous === null || from === 0 || Math.abs(from - to) < 2 || prefersReducedMotion()) {
      return;
    }
    if (typeof container.animate !== "function") {
      return;
    }
    container.animate(
      [
        { height: `${from}px`, overflow: "clip" },
        { height: `${to}px`, overflow: "clip" }
      ],
      { duration: HEIGHT_MS, easing: "cubic-bezier(0.2, 0.8, 0.2, 1)" }
    );
  }, [nav]);

  useEffect(() => {
    const container = contentRef.current;
    if (!container || typeof ResizeObserver === "undefined") {
      return;
    }
    const observer = new ResizeObserver(() => {
      heightRef.current = container.offsetHeight;
    });
    observer.observe(container);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    if (nav.previous === null) {
      return;
    }
    const heading = contentRef.current?.querySelector<HTMLElement>('[data-auth-entry="current"] h1');
    if (heading) {
      heading.tabIndex = -1;
      heading.focus({ preventScroll: true });
    }
  }, [nav]);

  return (
    <div className="relative z-10 text-text-primary">
      {/* İlk ekran: navbar + kart en az bir ekran yüksekliğidir; footer bunun altında kalır ve kaydırınca gelir. */}
      <div className="flex min-h-dvh flex-col">
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
              <div ref={contentRef} className="relative">
                {/* Çıkan katmanlar yalnız burada, zorunlu olarak boş kalan bu kapta (React çocuk yönetmez) tutulur. */}
                <div ref={ghostsRef} aria-hidden="true" className="pointer-events-none absolute inset-x-0 top-0" />
                <div key={pathname} data-auth-entry="current" className={`p-6 sm:p-8 ${TRANSITION_CLASS[transition]}`}>
                  {children}
                </div>
              </div>
            </div>
          </div>
        </main>
      </div>

      {footer ? <div className="relative">{footer}</div> : null}
      {/* Tema değişimi ana uygulamadaki sahneyle oynar (AppShell'deki katmanın aynısı). */}
      <ThemeTransitionLayer />
    </div>
  );
}
