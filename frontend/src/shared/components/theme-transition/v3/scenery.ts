/**
 * V3 gün doğumu manzarası: piksel basamaklı dağ sırtları ve piksel güneş diski (saf TS, deterministik).
 * SVG yolları tamsayı ızgarada üretilir ve `shape-rendering: crispEdges` ile keskin çizilir.
 * Rastgelelik yoktur: her çizimde aynı sırt çıkar (hidrasyon ve test güvenli).
 */
export const RIDGE_VIEW = { width: 480, height: 120 } as const;

type RidgeSpec = {
  /** Sırtın ortalama yüksekliği (ızgara satırı, üstten). */
  base: number;
  /** Tepe genliği (satır). */
  amp: number;
  /** Basamak genişliği (ızgara sütunu). */
  step: number;
  /** Faz kaydırma; katmanların tepeleri üst üste binmesin. */
  phase: number;
};

export const RIDGES = {
  far: { base: 46, amp: 26, step: 4, phase: 0.6 },
  mid: { base: 72, amp: 16, step: 6, phase: 2.1 },
  near: { base: 96, amp: 9, step: 8, phase: 4.4 }
} as const satisfies Record<string, RidgeSpec>;

function ridgeHeight(x: number, { base, amp, phase }: RidgeSpec): number {
  const u = x / RIDGE_VIEW.width;
  const wave =
    0.55 * Math.sin(u * Math.PI * 3.1 + phase) +
    0.3 * Math.sin(u * Math.PI * 7.3 + phase * 1.7) +
    0.15 * Math.sin(u * Math.PI * 15.7 + phase * 0.6);
  return Math.round(base - amp * wave);
}

/** Basamaklı sırt silueti (kapalı yol, alt kenar dolu). */
export function ridgePath(spec: RidgeSpec): string {
  const { width, height } = RIDGE_VIEW;
  let path = `M0 ${height}`;
  for (let x = 0; x < width; x += spec.step) {
    const y = ridgeHeight(x + spec.step / 2, spec);
    path += `V${y}H${Math.min(x + spec.step, width)}`;
  }
  return `${path}V${height}Z`;
}

/** Sırtın en üst 1 satırlık kenarı (gün ışığı yansıması). */
export function ridgeRimPath(spec: RidgeSpec): string {
  const { width } = RIDGE_VIEW;
  let path = "";
  for (let x = 0; x < width; x += spec.step) {
    const y = ridgeHeight(x + spec.step / 2, spec);
    path += `M${x} ${y}h${Math.min(spec.step, width - x)}v1h-${Math.min(spec.step, width - x)}z`;
  }
  return path;
}

/**
 * Piksel daire: `radius` yarıçaplı, (2r × 2r) ızgarada satır satır dikdörtgen.
 * `inset` > 0 iç halkayı (çekirdek) verir.
 */
export function pixelDiscPath(radius: number, inset = 0): string {
  const r = radius - inset;
  let path = "";
  for (let row = 0; row < radius * 2; row += 1) {
    const dy = row + 0.5 - radius;
    if (Math.abs(dy) >= r) {
      continue;
    }
    const half = Math.round(Math.sqrt(r * r - dy * dy));
    if (half > 0) {
      path += `M${radius - half} ${row}h${half * 2}v1h-${half * 2}z`;
    }
  }
  return path;
}
