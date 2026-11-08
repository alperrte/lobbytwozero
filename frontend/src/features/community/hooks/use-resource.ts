"use client";

import { useEffect, useEffectEvent, useState } from "react";
import { isAbort } from "../services/community-errors";

type Result<T> = { key: string; data?: T; error?: unknown };

/**
 * Tek kaynak okuma (gönderi, forum…). `key` değişince eski veri görünmez; `null` anahtar isteği durdurur.
 * Başarısızlık `error` olarak kalır ve çağıran yükleme/bulunamadı/yetkisiz durumlarını durum koduna göre ayırır.
 */
export function useResource<T>(key: string | null, fetcher: (signal: AbortSignal) => Promise<T>) {
  const [revision, setRevision] = useState(0);
  const [result, setResult] = useState<Result<T>>();
  const load = useEffectEvent(fetcher);
  const fullKey = key === null ? null : `${key}#${revision}`;

  useEffect(() => {
    if (fullKey === null) return;
    const controller = new AbortController();
    let active = true;
    load(controller.signal).then(
      (data) => active && setResult({ key: fullKey, data }),
      (error: unknown) => {
        if (active && !isAbort(error)) setResult({ key: fullKey, error });
      }
    );
    return () => {
      active = false;
      controller.abort();
    };
  }, [fullKey]);

  const current = fullKey !== null && result?.key === fullKey ? result : undefined;

  return {
    data: current?.data,
    error: current?.error,
    loading: fullKey !== null && !current,
    reload: () => setRevision((value) => value + 1),
    /** Sunucudan dönen güncel değeri yerine yazar (ör. düzenleme sonrası). */
    set: (data: T) => setResult((previous) => (previous && previous.key === fullKey ? { ...previous, data, error: undefined } : previous))
  };
}
