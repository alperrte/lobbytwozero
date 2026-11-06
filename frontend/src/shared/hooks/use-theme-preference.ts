"use client";

import { useSyncExternalStore } from "react";
import {
  applyThemePreference,
  parseThemePreference,
  subscribePreferences,
  type ThemePreference
} from "@/shared/lib/preferences/preferences";

function getSnapshot(): ThemePreference {
  return parseThemePreference(document.documentElement.dataset.themePref);
}

function getServerSnapshot(): ThemePreference {
  return "system";
}

export function useThemePreference() {
  const preference = useSyncExternalStore(
    subscribePreferences,
    getSnapshot,
    getServerSnapshot
  );

  return [preference, applyThemePreference] as const;
}
