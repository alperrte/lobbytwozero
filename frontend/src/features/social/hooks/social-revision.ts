"use client";

import { useSyncExternalStore } from "react";

/**
 * Sosyal verinin sürüm sayacı. Başarılı her işlem (kabul, ret, engel…) sayacı artırır; listeleri, sekme rozetlerini ve yan
 * panelleri okuyan hook'lar bu sayaca abonedir ve kendini yeniler. Böylece yan paneldeki "Kabul Et" arkadaş listesini ve
 * rozetleri de günceller; ayrı bir durum kütüphanesi gerekmez.
 */
let revision = 0;
const listeners = new Set<() => void>();

export function bumpSocialRevision(): void {
  revision += 1;
  for (const listener of listeners) {
    listener();
  }
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

function snapshot(): number {
  return revision;
}

export function useSocialRevision(): number {
  return useSyncExternalStore(subscribe, snapshot, () => 0);
}
