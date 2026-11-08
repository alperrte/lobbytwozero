"use client";

import { useCallback, useEffect, useState } from "react";
import { ApiHttpError } from "@/shared/lib/api/client";

export type RemoteState<T> =
  | { status: "idle" }
  | { status: "loading" }
  | { status: "error"; code: string | undefined }
  | { status: "ready"; data: T };

export type Remote<T> = { state: RemoteState<T>; retry: () => void };

/**
 * Tek bir uçtan veri okur. `key === null` iken istek atılmaz (`idle`). `load` kararlı olmalıdır (modül düzeyinde bir fonksiyon
 * ya da `useCallback`); anahtar ya da `load` değişince yeniden yüklenir. Hata ya da iptal profilin geri kalanını bozmaz:
 * çağıran `error` durumunu kendi bölümünde gösterir ve `retry` ile yeniden dener.
 */
export function useRemote<T>(key: string | null, load: (signal: AbortSignal) => Promise<T>): Remote<T> {
  const [result, setResult] = useState<{ key: string; attempt: number; state: RemoteState<T> } | null>(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (key === null) {
      return;
    }
    const controller = new AbortController();
    load(controller.signal)
      .then((data) => setResult({ key, attempt, state: { status: "ready", data } }))
      .catch((error: unknown) => {
        if (controller.signal.aborted) {
          return;
        }
        setResult({
          key,
          attempt,
          state: { status: "error", code: error instanceof ApiHttpError ? error.code : undefined }
        });
      });
    return () => controller.abort();
  }, [key, load, attempt]);

  const retry = useCallback(() => setAttempt((value) => value + 1), []);
  const current = result !== null && result.key === key && result.attempt === attempt ? result.state : null;
  const state: RemoteState<T> = key === null ? { status: "idle" } : (current ?? { status: "loading" });
  return { state, retry };
}
