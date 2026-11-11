"use client";

import { useEffect, useState } from "react";
import { loadReviewResource } from "../services/review-api";
import type { ReviewResource } from "../types/review";

const TIMEOUT_MS = 20000;

type Settled<T> = { key: string; revision: number; data?: T; error?: unknown };

/**
 * Review okuma kaynağı yükleyicisi. Kaynak anahtarı değişince (ör. başka oyuna geçiş) önceki istek iptal edilir ve eski
 * yanıt yeni ekranı EZEMEZ. Aynı anahtarın yeniden yüklenmesinde (`reload`) önceki veri korunur (`loading` yalnız bilgi verir).
 * `null` kaynak = bağımlı veri henüz hazır değil; istek atılmaz. Kişisel veri ortak önbelleğe yazılmaz.
 */
export function useReviewResource<T>(resource: ReviewResource | null) {
  const key = resource ? JSON.stringify(resource) : "";
  const [revision, setRevision] = useState(0);
  const [result, setResult] = useState<Settled<T>>();

  useEffect(() => {
    if (!key) return;
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), TIMEOUT_MS);
    let active = true;
    loadReviewResource(JSON.parse(key) as ReviewResource, controller.signal)
      .then(
        (data) => {
          if (active) setResult({ key, revision, data: data as T });
        },
        (error: unknown) => {
          if (active) setResult((previous) => ({ key, revision, data: previous?.key === key ? previous.data : undefined, error }));
        }
      )
      .finally(() => clearTimeout(timeout));
    return () => {
      active = false;
      clearTimeout(timeout);
      controller.abort();
    };
  }, [key, revision]);

  const sameKey = key !== "" && result?.key === key ? result : undefined;
  const settled = sameKey !== undefined && sameKey.revision === revision;
  return {
    data: sameKey?.data,
    error: settled ? sameKey.error : undefined,
    loading: key !== "" && !settled,
    reload: () => setRevision((value) => value + 1)
  };
}
