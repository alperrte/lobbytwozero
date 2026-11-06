"use client";

import { useSyncExternalStore } from "react";

type NavigatorWithUAData = Navigator & {
  userAgentData?: { platform?: string };
};

function subscribe(): () => void {
  return () => {};
}

function getSnapshot(): "Ctrl" | "⌘" {
  const nav = navigator as NavigatorWithUAData;
  const platform = nav.userAgentData?.platform ?? nav.platform ?? "";
  return /mac|iphone|ipad|ipod/i.test(platform) ? "⌘" : "Ctrl";
}

/**
 * Kısayol ipucunda gösterilecek değiştirici tuş (macOS'ta ⌘, diğerlerinde Ctrl).
 * Sunucu çiziminde "Ctrl" döner; macOS'ta hydration sonrası ⌘'ye geçer.
 */
export function useModifierKey(): "Ctrl" | "⌘" {
  return useSyncExternalStore(subscribe, getSnapshot, () => "Ctrl");
}
