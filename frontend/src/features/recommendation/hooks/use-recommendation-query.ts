"use client";

import { useEffect, useRef, useState } from "react";
import { recommendationCache } from "../services/recommendation-cache";
import { isAbortError, isConsentRequired } from "../services/recommendation-errors";
import { useRecommendationScope } from "./recommendation-scope";

type Result<T> = { key: string; data?: T; error?: unknown; loading: boolean };
export type QueryResult<T> = { data?: T; error?: unknown; loading: boolean; reload: () => void };

/**
 * `useHardwareQuery` deseni + hub önbelleği. `key === null` iken istek atılmaz (ör. kullanıcı henüz "Getir" demedi).
 * `cache: true` ise başarılı yanıt hesap kimliğiyle bellek içinde 2 dk tutulur; `reload()` önbelleği atlar.
 * `CONSENT_REQUIRED` hatası kapsama bildirilir (kapı yeniden açılır); iptal edilen istek durum değiştirmez.
 */
export function useRecommendationQuery<T>(key: string | null, loader: (signal: AbortSignal) => Promise<T>, options: { cache?: boolean } = {}): QueryResult<T> {
  const { accountId, consentRequired } = useRecommendationScope();
  const useCache = options.cache === true;
  const [result, setResult] = useState<Result<T>>(() => {
    const cached = key && useCache ? recommendationCache.get<T>(accountId, key) : undefined;
    return cached === undefined ? { key: "", loading: true } : { key: key as string, data: cached, loading: false };
  });
  const [revision, setRevision] = useState(0);
  const loaderRef = useRef(loader);
  const consentRef = useRef(consentRequired);
  const skipCache = useRef(false);
  useEffect(() => {
    loaderRef.current = loader;
    consentRef.current = consentRequired;
  });
  useEffect(() => {
    if (key === null) return;
    const controller = new AbortController();
    let active = true;
    const bypass = skipCache.current;
    skipCache.current = false;
    const cached = useCache && !bypass ? recommendationCache.get<T>(accountId, key) : undefined;
    const pending = cached !== undefined ? Promise.resolve(cached) : loaderRef.current(controller.signal);
    pending.then(
      (data) => {
        if (!active) return;
        if (useCache && cached === undefined) recommendationCache.set(accountId, key, data);
        setResult({ key, data, loading: false });
      },
      (error: unknown) => {
        if (!active || isAbortError(error)) return;
        if (isConsentRequired(error)) consentRef.current();
        setResult({ key, error, loading: false });
      }
    );
    return () => {
      active = false;
      controller.abort();
    };
  }, [key, revision, accountId, useCache]);
  const reload = () => {
    skipCache.current = true;
    setResult({ key: "", loading: true });
    setRevision((value) => value + 1);
  };
  if (key === null) return { loading: false, reload };
  const current = result.key === key ? result : { key, loading: true };
  return { data: current.data, error: current.error, loading: current.loading, reload };
}
