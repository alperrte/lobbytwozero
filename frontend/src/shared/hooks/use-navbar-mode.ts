"use client";

import { useSyncExternalStore } from "react";
import {
  applyNavbarMode,
  parseNavbarMode,
  subscribePreferences,
  type NavbarMode
} from "@/shared/lib/preferences/preferences";

function getSnapshot(): NavbarMode {
  return parseNavbarMode(
    document.documentElement.dataset.navbar === "fixed" ? "fixed" : "auto"
  );
}

function getServerSnapshot(): NavbarMode {
  return "auto";
}

export function useNavbarMode() {
  const mode = useSyncExternalStore(
    subscribePreferences,
    getSnapshot,
    getServerSnapshot
  );

  return [mode, applyNavbarMode] as const;
}
