"use client";

import { useSyncExternalStore } from "react";
import {
  parseThemePreference,
  subscribePreferences,
  type ResolvedTheme,
  type ThemePreference
} from "@/shared/lib/preferences/preferences";
import { changeTheme } from "@/shared/lib/theme/theme-transition";

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

  // Tercih değişimi tema geçiş sahnesi üzerinden uygulanır (hareket azaltma istenirse anında).
  return [preference, changeTheme] as const;
}

function getResolvedSnapshot(): ResolvedTheme {
  return document.documentElement.dataset.theme === "light" ? "light" : "dark";
}

function getResolvedServerSnapshot(): ResolvedTheme {
  return "dark";
}

/** Ekranda görünen tema (data-theme). "Sistem" tercihinde işletim sistemi seçimini yansıtır. */
export function useResolvedTheme(): ResolvedTheme {
  return useSyncExternalStore(
    subscribePreferences,
    getResolvedSnapshot,
    getResolvedServerSnapshot
  );
}
