"use client";

import { useEffect, useState } from "react";
import { loadLibraryResource } from "../services/library-api";
import type { LibraryResource } from "../types/library";

/** Bileşen oturum kimliğiyle key'lenir; kişisel veri ortak cache'e yazılmaz. */
export function useLibrary<T>(resource: LibraryResource) {
  const key = JSON.stringify(resource);
  const [revision, setRevision] = useState(0);
  const [result, setResult] = useState<{ key: string; revision: number; data?: T; error?: unknown }>();
  useEffect(() => {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 20000);
    let active = true;
    loadLibraryResource(JSON.parse(key) as LibraryResource, controller.signal).then(
      (data) => { if (active) setResult({ key, revision, data: data as T }); },
      (error: unknown) => { if (active) setResult({ key, revision, error }); }
    ).finally(() => clearTimeout(timeout));
    return () => { active = false; clearTimeout(timeout); controller.abort(); };
  }, [key, revision]);
  useEffect(() => {
    const refresh = () => { if (document.visibilityState === "visible") setRevision((value) => value + 1); };
    window.addEventListener("focus", refresh);
    document.addEventListener("visibilitychange", refresh);
    return () => { window.removeEventListener("focus", refresh); document.removeEventListener("visibilitychange", refresh); };
  }, []);
  const current = result?.key === key && result.revision === revision ? result : undefined;
  return { data: current?.data, error: current?.error, loading: !current, reload: () => setRevision((value) => value + 1) };
}
