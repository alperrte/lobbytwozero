"use client";

import { useEffect, useState } from "react";
import { loadGameResource } from "../services/game-api";
import type { GameResource } from "../types/game";

/** İstek değiştiğinde eski veri görünmez; odak dönüşünde kişiye özel içerik yeniden doğrulanır. */
export function useGameResource<T>(resource: GameResource, options: { retainWhileRefreshing?: boolean; refreshOnFocus?: boolean } = {}) {
  const key = JSON.stringify(resource);
  const [revision, setRevision] = useState(0);
  const [result, setResult] = useState<{ key: string; revision: number; data?: T; error?: unknown }>();
  useEffect(() => {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 12000);
    let active = true;
    loadGameResource(JSON.parse(key) as GameResource, controller.signal).then(
      (data) => { clearTimeout(timeout); if (active) setResult({ key, revision, data: data as T }); },
      (error: unknown) => { clearTimeout(timeout); if (active) setResult({ key, revision, error }); }
    );
    return () => { active = false; clearTimeout(timeout); controller.abort(); };
  }, [key, revision]);
  useEffect(() => {
    if (options.refreshOnFocus === false) return;
    const refresh = () => { if (document.visibilityState === "visible") setRevision((value) => value + 1); };
    window.addEventListener("focus", refresh);
    document.addEventListener("visibilitychange", refresh);
    return () => {
      window.removeEventListener("focus", refresh);
      document.removeEventListener("visibilitychange", refresh);
    };
  }, [options.refreshOnFocus]);
  const current = result?.key === key && result.revision === revision ? result : undefined;
  const retained = options.retainWhileRefreshing && result?.key === key ? result?.data : undefined;
  return { data: current?.data ?? (current?.error ? undefined : retained), error: current?.error, loading: !current, reload: () => setRevision((value) => value + 1) };
}
