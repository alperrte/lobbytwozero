/**
 * V3 sahne oynatıcısı: `[ms, değer, geçiş?]` izlerini Web Animations API anahtar karelerine çevirir.
 * Tüm izler geçişin toplam süresiyle oynar ve denetleyicinin başlangıç anına (startedAt) hizalanır;
 * böylece tema değişimi (setTimeout) ile görüntü aynı anda olur. Sahne kalkınca hepsi iptal edilir.
 */
export type TrackPoint = readonly [ms: number, value: string | number, easing?: string];

/** Bir anahtar kareden sonrakine kadar değeri sabit tutar (sprite karesi, ani sönme). */
export const HOLD = "step-end";

export function toKeyframes(property: string, points: readonly TrackPoint[], totalMs: number): Keyframe[] {
  if (points.length === 0) {
    return [];
  }
  const offset = (ms: number) => Math.min(1, Math.max(0, ms / totalMs));
  const frames: Keyframe[] = [];
  const first = points[0];
  if (first[0] > 0) {
    frames.push({ offset: 0, [property]: first[1], easing: HOLD });
  }
  let last = 0;
  for (const [ms, value, easing] of points) {
    // Sıra bozuksa (ör. yuvarlama) WAAPI hata verir; ofsetler azalmayan tutulur.
    last = Math.max(last, offset(ms));
    frames.push({ offset: last, [property]: value, easing: easing ?? "linear" });
  }
  if (last < 1) {
    frames.push({ offset: 1, [property]: points[points.length - 1][1] });
  }
  return frames;
}

export type ScenePlayer = {
  /** WAAPI var mı (jsdom ve çok eski tarayıcılarda yok: sahne durağan çizilir, tema yine değişir). */
  readonly live: boolean;
  track(element: Element | null | undefined, property: string, points: readonly TrackPoint[]): void;
  cancel(): void;
};

export function createScenePlayer(root: Element, totalMs: number, startedAt: number): ScenePlayer {
  const live = typeof root.animate === "function";
  const elapsed = Math.max(0, performance.now() - startedAt);
  const animations: Animation[] = [];
  return {
    live,
    track(element, property, points) {
      if (!live || !element || points.length === 0) {
        return;
      }
      const animation = element.animate(toKeyframes(property, points, totalMs), {
        duration: totalMs,
        fill: "both"
      });
      animation.currentTime = elapsed;
      animations.push(animation);
    },
    cancel() {
      animations.forEach((animation) => animation.cancel());
      animations.length = 0;
    }
  };
}

/** Görünür alan ve navbar alt kenarı (lambalar buradan sarkar). */
export function readViewport(): { width: number; height: number; headerBottom: number } {
  const header = document.querySelector("header");
  const bottom = header?.getBoundingClientRect().bottom ?? 0;
  return {
    width: window.innerWidth,
    height: window.innerHeight,
    headerBottom: bottom > 0 ? Math.round(bottom) : 64
  };
}
