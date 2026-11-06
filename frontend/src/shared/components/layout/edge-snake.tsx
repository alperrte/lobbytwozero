"use client";

import { useEffect, useId, useRef } from "react";
import { cn } from "@/shared/utils/cn";

/**
 * Işık izi katmanları: aynı noktada biten, uzunlukları 88 px'ten 8 px'e inen 10 ince çizgi.
 * Her biri düşük opaklıktadır; üst üste bindikleri yerde (başa doğru) iz parlar, uçlarda ise
 * yalnız en uzun katman kaldığı için basamaksız, yumuşak biçimde solar. Kalın glow yoktur;
 * çizgi çerçevenin 1 px'lik kenarlığına oturur. Renk kuyrukta kırmızı, başta pembedir.
 * `tail`: katmanın başlangıcının referans noktasının kaç px gerisinde olduğu (kuyruk başın
 * gerisinde daha uzundur), `len`: katman uzunluğu.
 */
const LAYER_COUNT = 10;
const LAYERS: ReadonlyArray<{
  tail: number;
  len: number;
  width: number;
  opacity: number;
  stroke: string;
}> = Array.from({ length: LAYER_COUNT }, (_, i) => {
  const len = Math.round(88 - i * (80 / (LAYER_COUNT - 1)));
  return {
    len,
    tail: Math.round(len * 0.7),
    width: i < LAYER_COUNT - 2 ? 1.75 : 2.25,
    opacity: i < LAYER_COUNT - 2 ? 0.17 : 0.3,
    stroke: i < 4 ? "stroke-primary" : i < 7 ? "stroke-secondary" : "stroke-accent"
  };
});

/**
 * Navbar çerçevesinde saat yönünde dolaşan tek bir kırmızı → pembe ışık izi.
 *
 * Üst kart `relative` olmalı ve kendi `border-radius` değerini taşımalıdır: bileşen yarıçapı ve
 * boyutu o karttan ölçer, çevre uzunluğunu hesaplar ve CSS değişkeni (`--edge-p`) olarak yazar.
 * Ölçüm yalnız mount ve boyut değişiminde yapılır, React render döngüsü üretmez.
 * İz çerçevenin 1px'lik kenarlığının üzerine oturur; içerikle etkileşmez (`pointer-events-none`).
 * Hareket azaltma tercihinde iz gizlenir ve yerine sade, sabit bir gradient çerçeve görünür.
 */
export function EdgeSnake() {
  const gradientId = useId();
  const svgRef = useRef<SVGSVGElement>(null);

  useEffect(() => {
    const svg = svgRef.current;
    const host = svg?.parentElement;
    if (!svg || !host) {
      return;
    }
    const target = svg;
    const card = host;
    const rects = target.querySelectorAll("rect");

    function layout() {
      const { width, height } = target.getBoundingClientRect();
      if (width === 0 || height === 0) {
        return;
      }
      const cardRadius = parseFloat(getComputedStyle(card).borderTopLeftRadius) || 0;
      // SVG, kartın iç kenarına (kenarlığın içine) oturur; iç yarıçap = dış yarıçap - kenarlık.
      const radius = Math.min(Math.max(cardRadius - 1, 0), width / 2, height / 2);
      for (const rect of rects) {
        rect.setAttribute("width", String(width));
        rect.setAttribute("height", String(height));
        rect.setAttribute("rx", String(radius));
      }
      const perimeter = 2 * (width + height) - 8 * radius + 2 * Math.PI * radius;
      target.style.setProperty("--edge-p", `${perimeter.toFixed(2)}px`);
    }

    layout();
    if (typeof ResizeObserver === "undefined") {
      return;
    }
    const observer = new ResizeObserver(layout);
    observer.observe(target);
    return () => observer.disconnect();
  }, []);

  return (
    <svg
      ref={svgRef}
      aria-hidden="true"
      focusable="false"
      className="pointer-events-none absolute inset-0 size-full overflow-visible"
    >
      <defs>
        <linearGradient id={gradientId} x1="0" y1="0" x2="1" y2="0">
          <stop offset="0" style={{ stopColor: "var(--ltz-primary)" }} />
          <stop offset="0.5" style={{ stopColor: "var(--ltz-secondary)" }} />
          <stop offset="1" style={{ stopColor: "var(--ltz-accent)" }} />
        </linearGradient>
      </defs>

      {/* Hareket azaltmada görünen sade statik çerçeve. */}
      <rect
        fill="none"
        strokeWidth={1.5}
        stroke={`url(#${gradientId})`}
        className="opacity-0 motion-reduce:opacity-40"
      />

      {LAYERS.map(({ tail, len, width, opacity, stroke }) => (
        <rect
          key={`${tail}-${len}`}
          fill="none"
          strokeWidth={width}
          strokeLinecap="butt"
          strokeOpacity={opacity}
          className={cn("edge-trail motion-reduce:hidden", stroke)}
          style={
            {
              "--tail": `${tail}px`,
              "--len": `${len}px`
            } as React.CSSProperties
          }
        />
      ))}
    </svg>
  );
}
