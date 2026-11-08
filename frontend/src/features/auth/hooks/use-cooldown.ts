"use client";

import { useEffect, useState } from "react";

/**
 * Saniye cinsinden geri sayım (429 `Retry-After`, "kodu yeniden gönder" beklemesi). `start(saniye)` sayacı başlatır;
 * sıfıra inene kadar her saniye bir azalır. Yalnız görsel kolaylıktır: asıl sınırı sunucu uygular.
 */
export function useCooldown() {
  const [remaining, setRemaining] = useState(0);

  useEffect(() => {
    if (remaining <= 0) {
      return;
    }
    const timer = setTimeout(() => setRemaining((value) => value - 1), 1000);
    return () => clearTimeout(timer);
  }, [remaining]);

  return { remaining, start: (seconds: number) => setRemaining(Math.max(0, Math.floor(seconds))) };
}
