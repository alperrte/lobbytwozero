"use client";

import { useEffect, useRef, useState } from "react";
import { isAbortError, isConsentRequired, retryAfter } from "../services/recommendation-errors";
import { useRecommendationScope } from "./recommendation-scope";

/**
 * Yazma/üretme eylemleri. Kilit (ref) çift tıklamada ikinci isteği hiç göndermez; 429/503 `Retry-After` süresince
 * eylem pasif kalır (`cooldown` saniye cinsinden geri sayar). Hata çağırana da döner ki geri alma yapılabilsin.
 */
export function useRecommendationMutation() {
  const { consentRequired } = useRecommendationScope();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<unknown>();
  const [cooldown, setCooldown] = useState(0);
  const lock = useRef(false);
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);
  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = window.setTimeout(() => setCooldown((value) => Math.max(0, value - 1)), 1000);
    return () => window.clearTimeout(timer);
  }, [cooldown]);

  async function run<T>(operation: () => Promise<T>): Promise<{ ok: true; value: T } | { ok: false; error: unknown } | undefined> {
    if (lock.current || cooldown > 0) return undefined;
    lock.current = true;
    setPending(true);
    setError(undefined);
    try {
      const value = await operation();
      return { ok: true, value };
    } catch (cause) {
      if (!isAbortError(cause) && mounted.current) {
        setError(cause);
        const wait = retryAfter(cause);
        if (wait) setCooldown(wait);
        if (isConsentRequired(cause)) consentRequired();
      }
      return { ok: false, error: cause };
    } finally {
      lock.current = false;
      if (mounted.current) setPending(false);
    }
  }

  return { pending, error, cooldown, run, clearError: () => setError(undefined) };
}
