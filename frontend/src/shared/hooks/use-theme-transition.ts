"use client";

import { useSyncExternalStore } from "react";
import {
  getThemeTransition,
  subscribeThemeTransition,
  type ThemeTransitionState
} from "@/shared/lib/theme/theme-transition";

function getServerSnapshot(): ThemeTransitionState {
  return null;
}

export function useThemeTransition(): ThemeTransitionState {
  return useSyncExternalStore(
    subscribeThemeTransition,
    getThemeTransition,
    getServerSnapshot
  );
}
