"use client";

import { useRef, useState } from "react";
import { mutateGame } from "../services/game-api";
import type { GameMutation } from "../types/game";

export function useGameMutation() {
  const inFlight = useRef(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<unknown>();
  const [saved, setSaved] = useState(false);
  async function run(action: GameMutation): Promise<{ ok: true; value: unknown } | { ok: false }> {
    if (inFlight.current) return { ok: false };
    inFlight.current = true; setPending(true); setError(undefined); setSaved(false);
    try {
      const value = await mutateGame(action); setSaved(true); return { ok: true, value };
    } catch (failure) { setError(failure); return { ok: false }; }
    finally { inFlight.current = false; setPending(false); }
  }
  return { run, pending, error, saved };
}
