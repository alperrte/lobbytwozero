"use client";

import { useEffect, useRef, useState } from "react";
import { ApiHttpError } from "@/shared/lib/api/client";

export function usePerformanceMutation() {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<unknown>();
  const controller = useRef<AbortController | null>(null);
  const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; controller.current?.abort(); }; }, []);
  const requiresReview = Boolean(error && (!(error instanceof ApiHttpError) || error.status === 409 || error.status >= 500));
  async function run<T>(operation: (signal: AbortSignal) => Promise<T>): Promise<{ value: T } | undefined> {
    if (controller.current || requiresReview) return;
    const active = new AbortController();
    controller.current = active;
    setPending(true);
    setError(undefined);
    try {
      const value = await operation(active.signal);
      return mounted.current ? { value } : undefined;
    } catch (cause) {
      if (mounted.current) setError(cause);
    } finally {
      controller.current = null;
      if (mounted.current) setPending(false);
    }
  }
  return { pending, error, requiresReview, run, reset: () => { if (!controller.current) setError(undefined); } };
}
