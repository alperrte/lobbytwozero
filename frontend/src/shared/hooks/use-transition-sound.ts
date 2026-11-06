"use client";

import { useSyncExternalStore } from "react";
import {
  applyTransitionSound,
  parseTransitionSound,
  subscribePreferences,
  type TransitionSound
} from "@/shared/lib/preferences/preferences";

function getSnapshot(): TransitionSound {
  return parseTransitionSound(document.documentElement.dataset.transitionSound);
}

function getServerSnapshot(): TransitionSound {
  return "on";
}

/** V3 tema geçişi sesi (on / off). Kalıcıdır; ses yalnız kullanıcı tema düğmesine bastığında çalar. */
export function useTransitionSound() {
  const sound = useSyncExternalStore(subscribePreferences, getSnapshot, getServerSnapshot);

  return [sound, applyTransitionSound] as const;
}
