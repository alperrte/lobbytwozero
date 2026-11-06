"use client";

import { useSyncExternalStore } from "react";
import {
  applySidebarCollapsed,
  subscribePreferences
} from "@/shared/lib/preferences/preferences";

function getSnapshot(): boolean {
  return document.documentElement.dataset.sidebar === "collapsed";
}

function getServerSnapshot(): boolean {
  return false;
}

export function useSidebarCollapsed() {
  const collapsed = useSyncExternalStore(
    subscribePreferences,
    getSnapshot,
    getServerSnapshot
  );

  return [collapsed, applySidebarCollapsed] as const;
}
