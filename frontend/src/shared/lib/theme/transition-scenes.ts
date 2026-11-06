import type { ResolvedTheme, TransitionPack } from "@/shared/lib/preferences/preferences";

/**
 * Tema geçişi sahne kaydı (registry). Her sahnenin hangi pakete ait olduğu ve zamanlaması burada tutulur;
 * denetleyici (theme-transition.ts) sahne içeriğini bilmez, yalnız bu kayda bakar.
 *
 * Yeni sahne eklemek: `ThemeScene`'e adı ekle, `sceneTimings`'e süreyi yaz, `sceneFor` ile pakete bağla,
 * sahneyi `components/theme-transition/theme-transition-layer.tsx` içindeki seçiciye kaydet.
 *
 * V1 (klasik, legacy): sunrise = Dark → Light, storm = Light → Dark.
 * V2 (taktik, legacy): daybreak = Dark → Light, blackout = Light → Dark.
 * V3 (taktik, varsayılan): v3-daybreak = Dark → Light, v3-blackout = Light → Dark.
 */
export type ThemeScene = "sunrise" | "storm" | "daybreak" | "blackout" | "v3-daybreak" | "v3-blackout";

export type SceneTiming = {
  /** Örtünün DOM'da kaldığı toplam süre (ms). */
  totalMs: number;
  /** Gerçek temanın uygulandığı an (ms): örtü ekranı kapladığında. */
  applyAtMs: number;
  /** Dar ekranda (<768px) kullanılan kısa süreler; yoksa masaüstü süresi geçerlidir. */
  compact?: { totalMs: number; applyAtMs: number };
};

export const sceneTimings: Record<ThemeScene, SceneTiming> = {
  sunrise: { totalMs: 1400, applyAtMs: 560 },
  storm: { totalMs: 1400, applyAtMs: 560 },
  // V2 sahneleri yüzde ile yazılmış CSS zaman çizelgesine göre: blackout %62, daybreak %66 noktasında tema değişir.
  blackout: { totalMs: 2200, applyAtMs: 1364, compact: { totalMs: 1700, applyAtMs: 1054 } },
  daybreak: { totalMs: 2200, applyAtMs: 1452, compact: { totalMs: 1700, applyAtMs: 1122 } },
  // V3: tema, son lamba söndüğü an (blackout) ve güneş ışığı yayıldığı an (daybreak) uygulanır.
  // Değerler components/theme-transition/v3/timeline.ts çizelgesiyle birebir aynıdır (testle korunur).
  "v3-blackout": { totalMs: 2600, applyAtMs: 1560, compact: { totalMs: 2200, applyAtMs: 1140 } },
  "v3-daybreak": { totalMs: 2600, applyAtMs: 1770, compact: { totalMs: 2200, applyAtMs: 1500 } }
};

/** V1'in eski dışa aktarımları; mevcut testler ve belgeler bu değerlere bakar. */
export const V1_TOTAL_MS = sceneTimings.sunrise.totalMs;
export const V1_APPLY_AT_MS = sceneTimings.sunrise.applyAtMs;

/** Pakete ve hedef temaya göre oynatılacak sahne; `minimal` hiç sahne oynatmaz. */
export function sceneFor(pack: TransitionPack, target: ResolvedTheme): ThemeScene | null {
  switch (pack) {
    case "v3":
      return target === "light" ? "v3-daybreak" : "v3-blackout";
    case "v2":
      return target === "light" ? "daybreak" : "blackout";
    case "v1":
      return target === "light" ? "sunrise" : "storm";
    default:
      return null;
  }
}

export function resolveSceneTiming(scene: ThemeScene, compact: boolean): { totalMs: number; applyAtMs: number } {
  const timing = sceneTimings[scene];
  return compact && timing.compact ? timing.compact : timing;
}
