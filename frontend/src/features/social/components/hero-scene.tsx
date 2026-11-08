import { useId } from "react";

/**
 * Sosyal başlık illüstrasyonu: kızıl ay (Light'ta güneş), katmanlı şehir silüeti ve çatıda bekleyen operatörler.
 * Tamamen SVG ve tokenlarla çizilir: gökyüzü `surface`, silüetler `scene-ink`, ışıklar marka tonları. Dark'ta gece şehri,
 * Light'ta sisli gün doğumu olur. Binalar deterministik üretilir (sunucu ve istemci çıktısı aynıdır). Animasyon yoktur.
 * Dekoratiftir (`aria-hidden`); sayfanın anlamı başlık metnindedir.
 */

const WIDTH = 1200;
const HEIGHT = 240;

/** Küçük, deterministik sayı üreteci (mulberry32). */
function random(seed: number) {
  let state = seed;
  return () => {
    state = (state + 0x6d2b79f5) | 0;
    let value = Math.imul(state ^ (state >>> 15), 1 | state);
    value = (value + Math.imul(value ^ (value >>> 7), 61 | value)) ^ value;
    return ((value ^ (value >>> 14)) >>> 0) / 4294967296;
  };
}

type Building = { x: number; width: number; top: number };

function skyline(seed: number, startX: number, minHeight: number, maxHeight: number, minWidth: number, maxWidth: number) {
  const next = random(seed);
  const buildings: Building[] = [];
  let x = startX;
  while (x < WIDTH) {
    const width = minWidth + Math.round(next() * (maxWidth - minWidth));
    const height = minHeight + Math.round(next() * (maxHeight - minHeight));
    buildings.push({ x, width, top: HEIGHT - height });
    x += width + Math.round(next() * 4);
  }
  return buildings;
}

function skylinePath(buildings: readonly Building[], seed: number): string {
  const next = random(seed);
  const parts = buildings.map(({ x, width, top }) => {
    const antenna = next() > 0.72 ? `M${x + width / 2 - 1} ${top}v-${10 + Math.round(next() * 18)}h2V${top}` : "";
    const step = next() > 0.6 ? `M${x} ${top + 8}h${Math.round(width * 0.3)}v-8` : "";
    return `M${x} ${HEIGHT}V${top}h${width}V${HEIGHT}z${antenna}${step}`;
  });
  return parts.join("");
}

function windows(buildings: readonly Building[], seed: number) {
  const next = random(seed);
  const lit: Array<{ x: number; y: number; tone: "primary" | "accent" }> = [];
  for (const { x, width, top } of buildings) {
    for (let y = top + 10; y < HEIGHT - 8; y += 9) {
      for (let column = x + 4; column < x + width - 5; column += 7) {
        if (next() > 0.86) {
          lit.push({ x: column, y, tone: next() > 0.7 ? "accent" : "primary" });
        }
      }
    }
  }
  return lit;
}

const far = skyline(11, 380, 70, 130, 18, 42);
const mid = skyline(29, 460, 50, 105, 26, 58);
const farPath = skylinePath(far, 5);
const midPath = skylinePath(mid, 7);
const midWindows = windows(mid, 13);
const embers = Array.from({ length: 14 }, (_, index) => {
  const next = random(100 + index);
  return { x: 560 + next() * 620, y: 20 + next() * 150, r: 0.8 + next() * 1.4, o: 0.25 + next() * 0.45 };
});

/** Çatıda duran operatör silüeti (ayak ucu = 0,0; sağa bakar). */
function Operator({ x, scale, mirrored = false }: { x: number; scale: number; mirrored?: boolean }) {
  const flip = mirrored ? -1 : 1;
  return (
    <g transform={`translate(${x} ${HEIGHT - 34}) scale(${scale * flip} ${scale})`} className="fill-scene-ink">
      {/* Bacaklar, gövde, yelek, kask ve tüfek */}
      <path d="M-9 0l3-26h5l1 12 2-12h5l2 26h-6l-2-14-2 14z" />
      <path d="M-11-26l1-26c0-5 4-8 9-8h4c5 0 9 3 9 8l1 26z" />
      <path d="M-6-62c0-7 4-11 9-11s9 4 9 11v4H-6z" />
      <path d="M8-46h30v3H8zM30-49h4v9h-4z" />
      <path className="fill-accent" d="M4-63h8v2.5H4z" />
    </g>
  );
}

export function HeroScene({ figures = 2 }: { figures?: 1 | 2 }) {
  const id = useId().replace(/:/g, "");
  return (
    <svg
      viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
      preserveAspectRatio="xMaxYMid slice"
      aria-hidden="true"
      focusable="false"
      className="absolute inset-0 size-full"
    >
      <defs>
        <linearGradient id={`${id}-haze`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" style={{ stopColor: "var(--ltz-primary)", stopOpacity: 0 }} />
          <stop offset="1" style={{ stopColor: "var(--ltz-primary)", stopOpacity: 0.62 }} />
        </linearGradient>
        <radialGradient id={`${id}-glow`}>
          <stop offset="0" style={{ stopColor: "var(--ltz-primary)", stopOpacity: 0.55 }} />
          <stop offset="1" style={{ stopColor: "var(--ltz-primary)", stopOpacity: 0 }} />
        </radialGradient>
      </defs>

      <rect width={WIDTH} height={HEIGHT} className="fill-surface" />
      <rect width={WIDTH} height={HEIGHT} fill={`url(#${id}-haze)`} />
      <circle cx="930" cy="78" r="190" fill={`url(#${id}-glow)`} />
      <ellipse cx="900" cy={HEIGHT - 40} rx="420" ry="70" fill={`url(#${id}-glow)`} />
      <circle cx="930" cy="80" r="54" className="fill-primary" opacity="0.92" />
      <circle cx="912" cy="66" r="9" className="fill-scene-ink" opacity="0.14" />
      <circle cx="948" cy="96" r="6" className="fill-scene-ink" opacity="0.12" />

      <path d={farPath} className="fill-scene-ink" opacity="0.5" />
      <path d={midPath} className="fill-scene-ink" opacity="0.82" />
      {midWindows.map((light) => (
        <rect
          key={`${light.x}-${light.y}`}
          x={light.x}
          y={light.y}
          width="3"
          height="2"
          className={light.tone === "accent" ? "fill-accent" : "fill-primary"}
          opacity="0.55"
        />
      ))}

      {/* Ön çatı ve operatörler */}
      <path d={`M640 ${HEIGHT}V${HEIGHT - 34}h560V${HEIGHT}z`} className="fill-scene-ink" />
      <path d={`M640 ${HEIGHT - 34}h560`} className="stroke-primary" strokeWidth="1.5" opacity="0.7" />
      {figures === 2 ? (
        <>
          <Operator x={1010} scale={1.05} />
          <Operator x={1110} scale={1.15} mirrored />
        </>
      ) : (
        <Operator x={1060} scale={1.15} mirrored />
      )}

      {embers.map((ember) => (
        <circle
          key={`${ember.x}-${ember.y}`}
          cx={ember.x}
          cy={ember.y}
          r={ember.r}
          className="fill-accent"
          opacity={ember.o}
        />
      ))}

    </svg>
  );
}
