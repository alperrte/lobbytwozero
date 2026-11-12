"use client";

import Image from "next/image";
import { useState, type ReactNode } from "react";
import { Icon } from "@/shared/components/ui/icons";
import { cn } from "@/shared/utils/cn";
import { safeExternalUrl } from "../services/discovery-query";

/**
 * Kapak adresi: kayıtlı kapak varsa o; yoksa Steam referansı olan oyunda Steam'in herkese açık mağaza başlık görseli
 * (AppID yalnız rakam olmalı). Görsel yoksa/açılmazsa GameCover nötr yer tutucuya düşer; uydurma görsel kullanılmaz.
 */
export function coverOf(game: { coverUrl?: string | null; externalReferences?: { provider: string; externalId: string }[] }): string | null {
  if (game.coverUrl) return game.coverUrl;
  const steam = game.externalReferences?.find((reference) => reference.provider === "STEAM" && /^\d{1,10}$/.test(reference.externalId));
  return steam ? `https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/${steam.externalId}/header.jpg` : null;
}

/**
 * Kapak görseli. Yalnız güvenli HTTPS adresi yüklenir; adres yoksa veya görsel açılamazsa
 * kırık görsel yerine nötr bir yer tutucu gösterilir (stok görsel kullanılmaz).
 */
export function GameCover({ url, className, priority = false, children }: { url: string | null | undefined; title?: string; className?: string; priority?: boolean; children?: ReactNode }) {
  const safe = safeExternalUrl(url);
  const [failed, setFailed] = useState<string>();
  const show = safe && failed !== safe;
  // IGDB kapakları dikey (poster) orandadır: 16:9 çerçevede kırpılmadan, bulanık arka planla ortalanır. Steam başlık görseli (16:9) çerçeveyi doldurur.
  const poster = !!safe && safe.includes("images.igdb.com");
  return <div className={cn("relative aspect-video overflow-hidden bg-surface-elevated", className)}>
    {show
      ? <>
        {poster && <Image unoptimized src={safe} alt="" aria-hidden="true" fill sizes="25vw" referrerPolicy="no-referrer" loading="lazy" className="scale-125 object-cover opacity-50 blur-xl" />}
        <Image unoptimized src={safe} alt="" fill sizes="(min-width: 1024px) 25vw, (min-width: 640px) 50vw, 100vw" referrerPolicy="no-referrer" loading={priority ? "eager" : "lazy"} onError={() => setFailed(safe)} className={poster ? "object-contain" : "object-cover"} />
      </>
      : <div data-testid="cover-placeholder" aria-hidden="true" className="flex h-full w-full items-center justify-center text-text-secondary"><Icon name="gamepad" width={36} height={36} /></div>}
    {children}
  </div>;
}
