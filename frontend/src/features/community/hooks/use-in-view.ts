"use client";

import { useEffect, useState, type RefObject } from "react";

/**
 * Öğe görüntü alanına (ve `rootMargin` kadar yakınına) ilk girdiğinde `true` olur ve öyle kalır. Her kartın yorumlarını
 * kart görünür olana dek yüklememek içindir (kontrolsüz toplu istek yok). IntersectionObserver olmayan ortamda (ör. test)
 * doğrudan `true` başlar.
 */
export function useInView(ref: RefObject<Element | null>, rootMargin = "240px"): boolean {
  const [seen, setSeen] = useState(() => typeof IntersectionObserver === "undefined");

  useEffect(() => {
    const element = ref.current;
    if (seen || !element) return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          setSeen(true);
          observer.disconnect();
        }
      },
      { rootMargin }
    );
    observer.observe(element);
    return () => observer.disconnect();
  }, [ref, rootMargin, seen]);

  return seen;
}
