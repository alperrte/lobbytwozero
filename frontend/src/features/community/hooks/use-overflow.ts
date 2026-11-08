"use client";

import { useSyncExternalStore, type RefObject } from "react";

/**
 * Öğenin içeriği kendi (sabit) yüksekliğinden uzun mu? Boyut ya da içerik değişince (ResizeObserver) güncellenir; kart
 * sabit boyutlu kaldığından "Devamını gör" yalnız metin gerçekten sığmadığında gösterilir. Sunucuda `false` döner.
 */
export function useOverflow(ref: RefObject<HTMLElement | null>): boolean {
  return useSyncExternalStore(
    (notify) => {
      const element = ref.current;
      if (!element || typeof ResizeObserver === "undefined") return () => {};
      const observer = new ResizeObserver(notify);
      observer.observe(element);
      Array.from(element.children).forEach((child) => observer.observe(child));
      return () => observer.disconnect();
    },
    () => {
      const element = ref.current;
      return element ? element.scrollHeight > element.clientHeight + 1 : false;
    },
    () => false
  );
}
