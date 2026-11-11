"use client";

import { useEffect, useRef, useState } from "react";

type Result<T> = { key: string; data?: T; error?: unknown; loading: boolean };

/** Account identity is part of every key; stale or aborted responses never cross accounts. */
export function usePerformanceQuery<T>(key: string, load: (signal: AbortSignal) => Promise<T>, enabled = true) {
  const [result, setResult] = useState<Result<T>>({ key: "", loading: true });
  const [revision, setRevision] = useState(0);
  const loader = useRef(load);
  useEffect(() => { loader.current = load; });
  useEffect(() => {
    if (!enabled) return;
    const controller = new AbortController();
    let active = true;
    loader.current(controller.signal).then(
      (data) => { if (active) setResult({ key, data, loading: false }); },
      (error: unknown) => { if (active) setResult({ key, error, loading: false }); }
    );
    return () => { active = false; controller.abort(); };
  }, [key, revision, enabled]);
  return {
    ...(enabled && result.key === key ? result : { key, loading: enabled }),
    reload: () => { setResult({ key, loading: true }); setRevision((value) => value + 1); }
  };
}
