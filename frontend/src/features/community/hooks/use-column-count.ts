"use client";

import { useSyncExternalStore, type RefObject } from "react";

/**
 * Kapsayıcının genişliğine göre sütun sayısı (her sütun en az `minWidth` px). Izgara satır yüksekliklerini eşitlediği için
 * doğal yükseklikli kartlar (masonry) sütunlara bölünerek yerleştirilir; sayı kapsayıcı boyutu değişince güncellenir.
 * Sunucuda ve ilk çizimde 1 döner (tek sütun, taşma yok).
 */
export function useColumnCount(ref: RefObject<HTMLElement | null>, minWidth = 320, gap = 16): number {
  return useSyncExternalStore(
    (notify) => {
      const element = ref.current;
      if (!element || typeof ResizeObserver === "undefined") return () => {};
      const observer = new ResizeObserver(notify);
      observer.observe(element);
      return () => observer.disconnect();
    },
    () => {
      const width = ref.current?.clientWidth ?? 0;
      return width > 0 ? Math.max(1, Math.floor((width + gap) / (minWidth + gap))) : 1;
    },
    () => 1
  );
}
