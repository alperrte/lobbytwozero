"use client";

import { useEffect, useRef, useState } from "react";
import { ApiHttpError } from "@/shared/lib/api/client";

type Result<T> = { key: string; data?: T; error?: unknown; loading: boolean };
export function useHardwareQuery<T>(key: string, loader: (signal: AbortSignal) => Promise<T>, enabled = true) {
  const [result, setResult] = useState<Result<T>>({ key: "", loading: true });
  const [revision, setRevision] = useState(0);
  const loaderRef = useRef(loader);
  useEffect(() => { loaderRef.current = loader; });
  useEffect(() => {
    if (!enabled) return;
    const controller = new AbortController();
    let active = true;
    loaderRef.current(controller.signal).then(
      (data) => { if (active) setResult({ key, data, loading: false }); },
      (error: unknown) => { if (active) setResult({ key, error, loading: false }); }
    );
    return () => { active = false; controller.abort(); };
  }, [key, revision, enabled]);
  const reload = () => { setResult({ key, loading: true }); setRevision((value) => value + 1); };
  const current = result.key === key && enabled ? result : { key, loading: enabled };
  return { ...current, reload };
}

export function useHardwareMutation(onSuccess?: () => void) {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<unknown>();
  const [success, setSuccess] = useState(false);
  const lock = useRef(false);
  const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  async function run<T>(operation: () => Promise<T>): Promise<T | undefined> {
    if (lock.current) return;
    lock.current = true; setPending(true); setError(undefined); setSuccess(false);
    try {
      const result = await operation();
      if (mounted.current) { setSuccess(true); onSuccess?.(); }
      return result;
    } catch (cause) {
      if (mounted.current) {
        setError(cause);
        // A lost response may have committed; reload before the user tries again.
        if (!(cause instanceof ApiHttpError) || cause.status === 409 || cause.status === 401 || cause.status >= 500) onSuccess?.();
      }
    } finally {
      lock.current = false;
      if (mounted.current) setPending(false);
    }
  }
  const requiresReview = Boolean(error && (!(error instanceof ApiHttpError) || error.status === 409 || error.status >= 500));
  return { pending, error, success, requiresReview, run };
}
