"use client";

import { useEffect, useId, useRef } from "react";
import {
  lapDuration,
  layerFraction,
  randomStart,
  roundedPerimeter,
  SNAKE_LAYERS,
  snakeKeyframes
} from "../services/snake-path";

type AuthCardSnakeProps = {
  /** İlk açılışta izin başlama gecikmesi (ms); kart giriş animasyonu bittikten sonra oynaması için. Verilmezse otomatik oynamaz. */
  initialDelayMs?: number;
};

/**
 * Kartın çerçevesinde TEK TUR atan kırmızı → pembe ışık izi. Ana navbar'daki `EdgeSnake` sürekli döner; bu bileşen ise
 * yalnız tetiklenince bir kez döner ve başladığı noktada biter. Üst öğe (kart) `relative` olmalı ve kendi
 * `border-radius` değerini taşımalıdır; yarıçap ve boyut ondan ölçülür.
 *
 * Tetikleyiciler: (1) `initialDelayMs` verildiyse açılıştan sonra bir kez, (2) fare kartın üzerine geldiğinde,
 * (3) klavye odağı karta girdiğinde. Çalışırken gelen yeni tetikleme yok sayılır (üst üste binme, döngü yok); tur
 * bitince yeniden tetiklenebilir ve her turun başlangıç noktası yeniden rastgele seçilir. Başlangıç noktası yalnız
 * efekt içinde (istemcide) seçilir, bu yüzden SSR çıktısı sabittir.
 *
 * React durumu kullanılmaz: animasyonlar Web Animations ile doğrudan SVG öğelerine verilir, render döngüsü oluşmaz.
 * Çizim vektördür (retina'da net); boyut `ResizeObserver` ile izlenir. "Hareketi azalt" açıkken iz oynamaz, yerine sabit
 * ince bir gradyan çerçeve görünür.
 */
export function AuthCardSnake({ initialDelayMs }: AuthCardSnakeProps) {
  const gradientId = useId();
  const svgRef = useRef<SVGSVGElement>(null);

  useEffect(() => {
    const svg = svgRef.current;
    const host = svg?.parentElement;
    if (!svg || !host) {
      return;
    }
    const surface = svg;
    const card = host;
    const rects = Array.from(surface.querySelectorAll<SVGRectElement>("rect"));
    const layers = Array.from(surface.querySelectorAll<SVGRectElement>("rect[data-layer]"));
    let perimeter = 0;
    let running = false;
    let disposed = false;
    let animations: Animation[] = [];
    let timer: number | undefined;

    function layout() {
      const width = card.clientWidth;
      const height = card.clientHeight;
      if (width === 0 || height === 0) {
        return;
      }
      // SVG kartın iç kenarına oturur; çizgi 1 px'lik kenarlığın ortasından geçsin diye yarım piksel dışa taşırılır.
      const radius = Math.max((parseFloat(getComputedStyle(card).borderTopLeftRadius) || 0) - 0.5, 0);
      for (const rect of rects) {
        rect.setAttribute("x", "-0.5");
        rect.setAttribute("y", "-0.5");
        rect.setAttribute("width", String(width + 1));
        rect.setAttribute("height", String(height + 1));
        rect.setAttribute("rx", String(radius));
      }
      perimeter = roundedPerimeter(width + 1, height + 1, radius);
    }

    function run() {
      if (running || disposed || document.hidden) {
        return;
      }
      if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
        return;
      }
      if (perimeter <= 0 || layers.length === 0 || typeof layers[0]?.animate !== "function") {
        return;
      }
      running = true;
      const start = randomStart();
      const duration = lapDuration(perimeter);
      surface.dataset.snakeState = "running";
      surface.dataset.snakeStart = start.toFixed(4);
      animations = layers.map((rect, index) => {
        const layer = SNAKE_LAYERS[index] ?? SNAKE_LAYERS[SNAKE_LAYERS.length - 1]!;
        const fraction = layerFraction(layer.px, perimeter);
        rect.style.strokeDasharray = `${fraction} ${1 - fraction}`;
        return rect.animate(snakeKeyframes(start, fraction, layer.opacity), { duration, easing: "linear" });
      });
      const settle = () => {
        if (disposed) {
          return;
        }
        running = false;
        surface.dataset.snakeState = "idle";
      };
      const last = animations[0];
      if (last) {
        last.addEventListener("finish", settle, { once: true });
        last.addEventListener("cancel", settle, { once: true });
      }
    }

    function onPointerEnter(event: PointerEvent) {
      if (event.pointerType !== "touch") {
        run();
      }
    }

    function onFocusIn(event: FocusEvent) {
      // Yalnız klavye odağı (focus-visible) ve odağın karta DIŞARIDAN girmesi tetikler; sayfa geçişindeki programatik odak değil.
      const target = event.target;
      if (!(target instanceof Element) || card.contains(event.relatedTarget as Node | null)) {
        return;
      }
      try {
        if (!target.matches(":focus-visible")) {
          return;
        }
      } catch {
        return;
      }
      run();
    }

    layout();
    card.addEventListener("pointerenter", onPointerEnter);
    card.addEventListener("focusin", onFocusIn);
    let observer: ResizeObserver | undefined;
    if (typeof ResizeObserver !== "undefined") {
      observer = new ResizeObserver(layout);
      observer.observe(card);
    }
    if (initialDelayMs !== undefined) {
      timer = window.setTimeout(run, initialDelayMs);
    }

    return () => {
      disposed = true;
      window.clearTimeout(timer);
      card.removeEventListener("pointerenter", onPointerEnter);
      card.removeEventListener("focusin", onFocusIn);
      observer?.disconnect();
      for (const animation of animations) {
        animation.cancel();
      }
    };
  }, [initialDelayMs]);

  return (
    <svg
      ref={svgRef}
      aria-hidden="true"
      focusable="false"
      data-testid="auth-card-snake"
      data-snake-state="idle"
      className="pointer-events-none absolute inset-0 size-full overflow-visible"
    >
      <defs>
        <linearGradient id={gradientId} x1="0" y1="0" x2="1" y2="0">
          <stop offset="0" style={{ stopColor: "var(--ltz-primary)" }} />
          <stop offset="0.5" style={{ stopColor: "var(--ltz-secondary)" }} />
          <stop offset="1" style={{ stopColor: "var(--ltz-accent)" }} />
        </linearGradient>
      </defs>

      {/* "Hareketi azalt": sabit, ince gradyan çerçeve. */}
      <rect fill="none" strokeWidth={1.5} stroke={`url(#${gradientId})`} className="opacity-0 motion-reduce:opacity-45" />

      {SNAKE_LAYERS.map((layer) => (
        <rect
          key={layer.px}
          data-layer=""
          pathLength={1}
          fill="none"
          strokeLinecap="butt"
          strokeWidth={layer.width}
          strokeOpacity={0}
          style={{ stroke: layer.color }}
          className="motion-reduce:hidden"
        />
      ))}
    </svg>
  );
}
