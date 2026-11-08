"use client";

import { useRef, useState } from "react";

export type ActionResult<R> = { ok: true; value: R } | { ok: false; skipped?: boolean };

/**
 * Tek uçuşlu eylem: bir istek sürerken aynı eylem tekrar tetiklenmez (çift tıklama, Enter tekrarı). Başarısız sonuç
 * `error` olarak kalır ve çağıran başarı mesajı göstermez; sonuç yalnız `ok: true` ise başarıdır.
 */
export function useAction<A extends unknown[], R>(action: (...args: A) => Promise<R>) {
  const inFlight = useRef(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<unknown>();

  async function run(...args: A): Promise<ActionResult<R>> {
    if (inFlight.current) return { ok: false, skipped: true };
    inFlight.current = true;
    setPending(true);
    setError(undefined);
    try {
      return { ok: true, value: await action(...args) };
    } catch (failure) {
      setError(failure);
      return { ok: false };
    } finally {
      inFlight.current = false;
      setPending(false);
    }
  }

  return { run, pending, error, clearError: () => setError(undefined) };
}
