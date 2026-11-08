/**
 * Kart çevresinde tek tur atan ışık izinin hesapları. İz, `pathLength="1"` ile normalleştirilmiş bir <rect> çevresinde
 * `stroke-dasharray` + `stroke-dashoffset` ile çizilir; çevre uzunluğu 1 birimdir ve yol sol üst köşe yayından sonra
 * saat yönünde ilerler. Başlangıç noktası bu birimdeki bir kesirdir (0 ≤ s < 1).
 */

/** İz katmanları: aynı başta biten, uzunluğu azalan ince çizgiler (EdgeSnake'in kuyruk tekniği). Uzunluk px'tir. */
export const SNAKE_LAYERS: ReadonlyArray<{ px: number; width: number; opacity: number; color: string }> = [
  { px: 150, width: 2, opacity: 0.26, color: "var(--ltz-primary)" },
  { px: 118, width: 2, opacity: 0.26, color: "var(--ltz-primary)" },
  { px: 90, width: 2, opacity: 0.26, color: "var(--ltz-primary)" },
  { px: 66, width: 2, opacity: 0.26, color: "var(--ltz-secondary)" },
  { px: 44, width: 2.25, opacity: 0.3, color: "var(--ltz-secondary)" },
  { px: 28, width: 2.5, opacity: 0.36, color: "var(--ltz-accent)" },
  { px: 14, width: 2.75, opacity: 0.44, color: "var(--ltz-accent)" }
];

/** Bir turun süresi (ms) sınırları; hız çevre uzunluğuna göre sabit tutulur. */
export const SNAKE_MIN_MS = 1500;
export const SNAKE_MAX_MS = 2600;
const SNAKE_PX_PER_MS = 0.95;

/** [0, 1) aralığında rastgele başlangıç. `rng` testte sabitlenir. */
export function randomStart(rng: () => number = Math.random): number {
  const value = rng();
  return Number.isFinite(value) ? Math.min(Math.max(value, 0), 0.999999) : 0;
}

/** Yuvarlatılmış köşeli dikdörtgenin çevresi (px). */
export function roundedPerimeter(width: number, height: number, radius: number): number {
  const r = Math.min(Math.max(radius, 0), width / 2, height / 2);
  return 2 * (width + height) - 8 * r + 2 * Math.PI * r;
}

export function lapDuration(perimeterPx: number): number {
  return Math.round(Math.min(SNAKE_MAX_MS, Math.max(SNAKE_MIN_MS, perimeterPx / SNAKE_PX_PER_MS)));
}

/** Katmanın çevreye oranı (0–1); çevre bilinmiyorsa 0. Tüm kuyruk çevreyi aşamaz. */
export function layerFraction(layerPx: number, perimeterPx: number): number {
  if (!(perimeterPx > 0)) {
    return 0;
  }
  return Math.min(layerPx / perimeterPx, 0.4);
}

/**
 * Bir katman için Web Animations anahtar kareleri. Baş, `start` noktasından çıkıp tam bir tur sonra aynı noktaya varır:
 * baş p konumundayken görünen parça [p - fraction, p] aralığıdır, bu da dashoffset = fraction - p demektir.
 * Başlangıçta ve bitişte kısa süre solar (ani görünüp kaybolmaz).
 */
export function snakeKeyframes(start: number, fraction: number, opacity: number): Keyframe[] {
  const from = fraction - start;
  return [
    { offset: 0, strokeDashoffset: String(from), strokeOpacity: 0 },
    { offset: 0.06, strokeOpacity: opacity },
    { offset: 0.92, strokeOpacity: opacity },
    { offset: 1, strokeDashoffset: String(from - 1), strokeOpacity: 0 }
  ];
}
