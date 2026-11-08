"use client";

import { useEffect, useState } from "react";
import { useSocialRevision } from "./social-revision";

type ResourceState<T> = {
  key: string;
  data?: T;
  error?: unknown;
};

/**
 * Tek seferlik okuma (sayaçlar, davet kodu). Sosyal sürüm değişince yeniden okunur; yenileme sırasında eski veri ekranda kalır
 * (işlem sonrası rozetler yanıp sönmez). İlk yükleme bitmeden `loading` doğrudur.
 */
export function useSocialResource<T>(load: (signal: AbortSignal) => Promise<T>) {
  const revision = useSocialRevision();
  const [retry, setRetry] = useState(0);
  const [state, setState] = useState<ResourceState<T>>();
  const key = `${revision}:${retry}`;

  useEffect(() => {
    const controller = new AbortController();
    load(controller.signal).then(
      (data) => setState({ key, data }),
      (error: unknown) => {
        if (!controller.signal.aborted) {
          setState((previous) => ({ key, data: previous?.data, error }));
        }
      }
    );
    return () => controller.abort();
  }, [key, load]);

  return {
    data: state?.data,
    error: state?.key === key ? state.error : undefined,
    loading: state === undefined,
    refreshing: state !== undefined && state.key !== key,
    reload: () => setRetry((value) => value + 1)
  };
}
