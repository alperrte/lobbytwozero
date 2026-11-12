"use client";

import { useCallback, useEffect, useState } from "react";

/**
 * 429 `Retry-After` geri sayımı. `start(seconds)` ile başlar; süre dolana kadar `remaining > 0` olur ve çağıran eylemi kapalı
 * tutar. Otomatik yeniden deneme YAPMAZ; süre dolunca kullanıcı eylemi kendisi tekrar başlatır.
 */
export function useRetryCountdown() {
  const [until, setUntil] = useState<number | null>(null);
  const [remaining, setRemaining] = useState(0);

  useEffect(() => {
    if (until === null) return;
    const tick = () => {
      const left = Math.max(0, Math.ceil((until - Date.now()) / 1000));
      setRemaining(left);
      if (left === 0) setUntil(null);
    };
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, [until]);

  // Kararlı kimlik: `start`, bir efektin bağımlılığı olarak kullanıldığında geri sayımı her render'da yeniden başlatmamalı.
  const start = useCallback((seconds: number) => {
    const safe = Math.max(1, Math.min(3600, Math.ceil(seconds)));
    setRemaining(safe);
    setUntil(Date.now() + safe * 1000);
  }, []);

  return { remaining, start };
}
