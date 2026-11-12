"use client";

import { useEffect, useRef, useState } from "react";

const TIMEOUT_MS = 20000;

export type ActionResult<T> = { ok: true; data: T } | { ok: false; error: unknown };

/**
 * Tek bir yazma eylemi için ortak çalıştırıcı: aynı anda tek istek (çift gönderim engellenir), zaman aşımı, bileşen
 * kapanınca iptal. Sonuç sunucudan gelmeden başarı bildirilmez; ağ hatasında istek OTOMATİK tekrarlanmaz.
 */
export function useReviewAction() {
  const inFlight = useRef(false);
  const controller = useRef<AbortController | null>(null);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<unknown>();

  useEffect(
    () => () => {
      controller.current?.abort();
    },
    []
  );

  async function run<T>(action: (signal: AbortSignal) => Promise<T>): Promise<ActionResult<T> | null> {
    if (inFlight.current) return null;
    inFlight.current = true;
    const request = new AbortController();
    controller.current = request;
    const timeout = setTimeout(() => request.abort(), TIMEOUT_MS);
    setPending(true);
    setError(undefined);
    try {
      const data = await action(request.signal);
      return { ok: true, data };
    } catch (failure) {
      setError(failure);
      return { ok: false, error: failure };
    } finally {
      clearTimeout(timeout);
      inFlight.current = false;
      setPending(false);
    }
  }

  return { run, pending, error, clearError: () => setError(undefined) };
}
