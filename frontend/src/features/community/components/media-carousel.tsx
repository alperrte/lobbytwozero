"use client";

import { useEffect, useId, useRef, useState, type KeyboardEvent } from "react";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { cn } from "@/shared/utils/cn";
import { ModalDialog } from "./community-ui";

export type MediaItem = { key: string; src: string; alt: string; width?: number; height?: number };

/** Bağlama göre boyut: akış/Gönderilerim kartı, detay, oluşturma önizlemesi ve tam boy görüntüleyici. */
export type CarouselVariant = "feed" | "detail" | "preview" | "viewer";

/**
 * Ortak medya alanı: TÜM gönderi fotoğrafları (yatay/dikey ayrımı olmadan; akış, Gönderilerim, detay, çoklu görsel slider'ı ve
 * oluşturma önizlemesi) aynı 4:3 çerçevede gösterilir: masaüstünde en çok 640 × 480 px, mobilde genişliğe uyar (4:3 korunur),
 * kartın ortasında. Çerçevenin kenarlığı, arka planı, köşe kırpması ve dış sarmalayıcı boşluğu yoktur. Fotoğraf HİÇBİR kenarından
 * kesilmez: `object-fit: contain` + `object-position: center` ile orijinal oranında, esnetilmeden ve yakınlaştırılmadan tamamı
 * sığar; oran farkından doğan boşluk kartın kendi arka planıdır. Slider'da çerçeve sabittir (fotoğraf değişince boyut değişmez),
 * her fotoğraf kendi oranıyla bütünüyle görünür. Orijinal dosya değişmez; tıklanınca görüntüleyicide açılır.
 */
const MEDIA_FRAME = "relative mx-auto aspect-[4/3] w-full max-w-[40rem] overflow-hidden";
const VIEWER_FRAME = "relative mx-auto h-[calc(100dvh-9rem)] max-h-[56rem] w-full overflow-hidden bg-background";

const clamp = (value: number, length: number) => Math.min(Math.max(value, 0), Math.max(length - 1, 0));

const arrowClass =
  "absolute top-1/2 z-10 inline-flex size-9 sm:size-11 -translate-y-1/2 items-center justify-center rounded-full border border-border bg-background/90 text-text-primary shadow-float transition-colors hover:border-primary hover:text-brand-text disabled:pointer-events-none disabled:opacity-35 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus";

/**
 * Çoklu görsel galerisi (tek bileşen; akış, detay, önizleme ve görüntüleyici aynısını kullanır).
 * - Aynı anda tek görsel; yerel kaydırma yakalaması (scroll-snap) mobilde parmakla, masaüstünde oklarla ve klavyeyle gezinir.
 * - Oklar her zaman görünür (yalnız hover'a bağlı değil); uçlarda devre dışı kalır. Otomatik kaydırma yoktur.
 * - Görsel her yerde `object-contain` + `object-center` ile tamamı görünür (kırpma yok); çerçeve sabittir.
 * - `index`/`onIndexChange` verilirse dışarıdan denetlenir (kaldırma/sıralamada aktif sayfa doğru kalır).
 */
export function MediaCarousel({
  items,
  variant,
  label,
  index,
  initialIndex = 0,
  onIndexChange,
  onOpen,
  className
}: {
  items: readonly MediaItem[];
  variant: CarouselVariant;
  label: string;
  index?: number;
  /** Denetimsiz kullanımda ilk sayfa (görüntüleyici tıklanan görselle açılır). */
  initialIndex?: number;
  onIndexChange?: (index: number) => void;
  onOpen?: (index: number) => void;
  className?: string;
}) {
  const t = useTranslations("Community.carousel");
  const scroller = useRef<HTMLDivElement>(null);
  const settleTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const statusId = useId();
  const [inner, setInner] = useState(initialIndex);
  const count = items.length;
  const active = clamp(index ?? inner, count);

  function reducedMotion(): boolean {
    return typeof window !== "undefined" && window.matchMedia?.("(prefers-reduced-motion: reduce)").matches === true;
  }

  function select(next: number, behavior: ScrollBehavior = reducedMotion() ? "auto" : "smooth") {
    const target = clamp(next, count);
    const element = scroller.current;
    element?.scrollTo?.({ left: target * element.clientWidth, behavior });
    setInner(target);
    onIndexChange?.(target);
  }

  // Dışarıdan değişen (ya da öğe sayısı düşünce kısılan) aktif sayfaya anında hizalanır; kullanıcı kaydırmasıyla çakışmaz.
  useEffect(() => {
    const element = scroller.current;
    if (!element || element.clientWidth === 0) return;
    if (Math.round(element.scrollLeft / element.clientWidth) !== active) {
      element.scrollTo?.({ left: active * element.clientWidth, behavior: "auto" });
    }
  }, [active, count]);

  // Pencere/çerçeve genişliği değişince aktif sayfa hizası korunur.
  useEffect(() => {
    function realign() {
      const element = scroller.current;
      if (element) element.scrollTo?.({ left: active * element.clientWidth, behavior: "auto" });
    }
    window.addEventListener("resize", realign);
    return () => window.removeEventListener("resize", realign);
  }, [active]);

  function onScroll() {
    const element = scroller.current;
    if (!element || element.clientWidth === 0) return;
    const next = clamp(Math.round(element.scrollLeft / element.clientWidth), count);
    if (next !== active) {
      setInner(next);
      onIndexChange?.(next);
    }
    // Kaydırma durunca en yakın sayfaya hizala (yerel yakalama yoksa ya da yarıda kalırsa görsel iki sayfa arasında kalmaz).
    if (settleTimer.current) clearTimeout(settleTimer.current);
    settleTimer.current = setTimeout(() => {
      const el = scroller.current;
      if (!el || el.clientWidth === 0) return;
      const target = clamp(Math.round(el.scrollLeft / el.clientWidth), count) * el.clientWidth;
      if (Math.abs(el.scrollLeft - target) > 2) el.scrollTo?.({ left: target, behavior: reducedMotion() ? "auto" : "smooth" });
    }, 160);
  }

  useEffect(() => () => {
    if (settleTimer.current) clearTimeout(settleTimer.current);
  }, []);

  function onKeyDown(event: KeyboardEvent) {
    const keys: Record<string, number> = { ArrowLeft: active - 1, ArrowRight: active + 1, Home: 0, End: count - 1 };
    if (!(event.key in keys)) return;
    event.preventDefault();
    select(keys[event.key] as number);
  }

  if (count === 0) return null;
  const multiple = count > 1;

  return (
    <div
      role="region"
      aria-roledescription={t("roleDescription")}
      aria-label={label}
      className={cn(variant === "viewer" ? VIEWER_FRAME : MEDIA_FRAME, className)}
    >
      <div
        ref={scroller}
        tabIndex={multiple ? 0 : undefined}
        aria-label={multiple ? t("scroller", { count }) : undefined}
        aria-describedby={statusId}
        onScroll={onScroll}
        onKeyDown={multiple ? onKeyDown : undefined}
        className="flex size-full snap-x snap-mandatory overflow-x-auto overscroll-x-contain [scrollbar-width:none] focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-focus [&::-webkit-scrollbar]:hidden"
      >
        {items.map((item, position) => (
          <div
            key={item.key}
            role="group"
            aria-roledescription={t("slide")}
            aria-label={t("slideLabel", { current: position + 1, total: count })}
            // Görünmeyen sayfalar ekran okuyucudan gizlenir ve Tab sırasından çıkar. `inert` KULLANILMAZ: Chromium inert
            // öğeleri kaydırma-yakalama hedefi saymaz ve görsel iki sayfa arasında kalır.
            aria-hidden={position !== active ? true : undefined}
            className="flex size-full shrink-0 snap-center snap-always items-center justify-center"
          >
            <Slide item={item} position={position} onOpen={onOpen} current={position === active} />
          </div>
        ))}
      </div>

      <p id={statusId} aria-live="polite" className="sr-only">
        {t("status", { current: active + 1, total: count })}
      </p>

      {multiple ? (
        <>
          <span
            aria-hidden="true"
            className="pointer-events-none absolute end-2 top-2 z-10 rounded-control border border-border bg-background/90 px-2 py-0.5 font-display text-xs font-semibold tabular-nums text-text-primary"
          >
            {active + 1}/{count}
          </span>
          <button type="button" className={cn(arrowClass, "start-2")} onClick={() => select(active - 1)} disabled={active === 0} aria-label={t("previous")}>
            <Icon name="chevron" className="size-5 rotate-90" />
          </button>
          <button type="button" className={cn(arrowClass, "end-2")} onClick={() => select(active + 1)} disabled={active === count - 1} aria-label={t("next")}>
            <Icon name="chevron" className="size-5 -rotate-90" />
          </button>
          <div className="absolute inset-x-0 bottom-1 z-10 flex justify-center">
            <ul className="flex items-center rounded-full bg-background/80 px-1">
              {items.map((item, position) => (
                <li key={item.key}>
                  <button
                    type="button"
                    onClick={() => select(position)}
                    aria-label={t("goTo", { number: position + 1 })}
                    aria-current={position === active ? "true" : undefined}
                    className="group inline-flex size-6 items-center justify-center focus-visible:outline-2 focus-visible:outline-offset-0 focus-visible:outline-focus"
                  >
                    <span
                      aria-hidden="true"
                      className={cn(
                        "block rounded-full transition-all",
                        position === active ? "h-2 w-5 bg-primary" : "size-2 bg-text-secondary/60 group-hover:bg-text-primary"
                      )}
                    />
                  </button>
                </li>
              ))}
            </ul>
          </div>
        </>
      ) : null}
    </div>
  );
}

function Slide({ item, position, onOpen, current }: { item: MediaItem; position: number; onOpen?: (index: number) => void; current: boolean }) {
  const t = useTranslations("Community.carousel");
  const [broken, setBroken] = useState(false);
  if (broken) {
    return <p className="px-4 text-center text-sm text-text-secondary">{t("broken")}</p>;
  }
  const image = (
    // Gateway ya da yerel blob adresi; çerçeve sabit olduğundan boyut sıçraması yoktur, optimizasyon vekili uygulanamaz.
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={item.src}
      alt={item.alt}
      width={item.width}
      height={item.height}
      loading={position === 0 ? "eager" : "lazy"}
      decoding="async"
      draggable={false}
      onError={() => setBroken(true)}
      className="size-full object-contain object-center"
    />
  );
  return onOpen ? (
    <button
      type="button"
      onClick={() => onOpen(position)}
      tabIndex={current ? 0 : -1}
      aria-label={t("open", { number: position + 1 })}
      className="size-full cursor-zoom-in focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-focus"
    >
      {image}
    </button>
  ) : (
    image
  );
}

/** Tam boy görüntüleyici: aynı carousel, `<dialog>` kipinde (odak içeride kalır, Escape kapatır, odak tetikleyiciye döner). */
export function ImageViewer({
  items,
  startIndex,
  label,
  onClose
}: {
  items: readonly MediaItem[];
  /** Açık değilse `null`. */
  startIndex: number | null;
  label: string;
  onClose: () => void;
}) {
  const t = useTranslations("Community.carousel");
  return (
    <ModalDialog open={startIndex !== null} onClose={onClose} title={t("viewer")} wide>
      {startIndex !== null ? <MediaCarousel items={items} variant="viewer" label={label} initialIndex={startIndex} key={startIndex} /> : null}
    </ModalDialog>
  );
}
