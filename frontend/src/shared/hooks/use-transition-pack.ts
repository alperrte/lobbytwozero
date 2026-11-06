"use client";

import { useSyncExternalStore } from "react";
import {
  applyTransitionPack,
  defaultTransitionPack,
  parseTransitionPack,
  subscribePreferences,
  type TransitionPack
} from "@/shared/lib/preferences/preferences";

function getSnapshot(): TransitionPack {
  return parseTransitionPack(document.documentElement.dataset.transition);
}

function getServerSnapshot(): TransitionPack {
  return defaultTransitionPack;
}

/** Tema geçişi paketi (v3 / v2 legacy / v1 / minimal). Kalıcıdır ve tema seçiminden bağımsızdır. */
export function useTransitionPack() {
  const pack = useSyncExternalStore(subscribePreferences, getSnapshot, getServerSnapshot);

  return [pack, applyTransitionPack] as const;
}
