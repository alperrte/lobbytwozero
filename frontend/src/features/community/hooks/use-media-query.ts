"use client";

import { useSyncExternalStore } from "react";

/**
 * CSS medya sorgusunun anlık durumu. Sunucuda ve ilk hidrasyonda `false` döner (dar/mobil varsayımı); istemcide gerçek
 * değer gelir ve değişimde güncellenir. Yalnız veri yükleme/etkileşim kararları içindir; yerleşim CSS'tedir.
 */
export function useMediaQuery(query: string): boolean {
  return useSyncExternalStore(
    (notify) => {
      const media = window.matchMedia(query);
      media.addEventListener("change", notify);
      return () => media.removeEventListener("change", notify);
    },
    () => window.matchMedia(query).matches,
    () => false
  );
}
