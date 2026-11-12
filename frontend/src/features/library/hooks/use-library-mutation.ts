"use client";

import { useEffect, useRef, useState } from "react";
import { mutateLibrary } from "../services/library-api";
import type { LibraryMutation } from "../types/library";

export function useLibraryMutation() {
  const inFlight = useRef(false);
  const controller = useRef<AbortController | null>(null);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<unknown>();
  const [saved, setSaved] = useState(false);
  useEffect(() => () => controller.current?.abort(), []);
  async function run(actions: LibraryMutation | LibraryMutation[]) {
    if (inFlight.current) return false;
    inFlight.current = true;
    const request = new AbortController();
    controller.current = request;
    const timeout = setTimeout(() => request.abort(), 20000);
    setPending(true); setError(undefined); setSaved(false);
    try {
      for (const action of Array.isArray(actions) ? actions : [actions]) await mutateLibrary(action, request.signal);
      if (!request.signal.aborted) setSaved(true);
      return !request.signal.aborted;
    } catch (failure) {
      setError(failure);
      return false;
    } finally {
      clearTimeout(timeout); inFlight.current = false; setPending(false);
    }
  }
  return { run, pending, error, saved };
}
