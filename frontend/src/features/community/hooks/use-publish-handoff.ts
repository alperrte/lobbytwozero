"use client";

import { useEffect } from "react";
import { clearReveal, finishPublish, usePublishState } from "../services/publish-flow";

/**
 * Paylaşım sonrası liste ekranının görevi: liste sunucudan okunup hazır olunca (`ready`) tam ekran yükleme katmanı
 * kaldırılır. Yeni gönderi sunucudan okunan listede zaten yer aldığı için yerel kopya eklenmez ve kart yinelenmez.
 * Dönen `revealId`, listede kısa giriş animasyonuyla belirecek gönderidir (animasyondan sonra temizlenir).
 */
export function usePublishHandoff(ready: boolean): string | null {
  const { phase, revealId } = usePublishState();

  useEffect(() => {
    if (phase === "awaitingList" && ready) finishPublish();
  }, [phase, ready]);

  useEffect(() => {
    if (revealId === null) return;
    const timer = setTimeout(clearReveal, 1200);
    return () => clearTimeout(timer);
  }, [revealId]);

  return revealId;
}
