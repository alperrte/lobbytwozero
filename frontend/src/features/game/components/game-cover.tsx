"use client";

import Image from "next/image";
import { useState } from "react";
import { Icon } from "@/shared/components/ui/icons";
import { cn } from "@/shared/utils/cn";
import { safeExternalUrl } from "../services/discovery-query";

/**
 * Kapak görseli. Yalnız güvenli HTTPS adresi yüklenir; adres yoksa veya görsel açılamazsa
 * kırık görsel yerine nötr bir yer tutucu gösterilir (stok görsel kullanılmaz).
 */
export function GameCover({ url, className, priority = false }: { url: string | null | undefined; title?: string; className?: string; priority?: boolean }) {
  const safe = safeExternalUrl(url);
  const [failed, setFailed] = useState<string>();
  const show = safe && failed !== safe;
  return <div className={cn("relative aspect-video overflow-hidden bg-surface-elevated", className)}>
    {show
      ? <Image unoptimized src={safe} alt="" fill sizes="(min-width: 1024px) 25vw, (min-width: 640px) 50vw, 100vw" referrerPolicy="no-referrer" loading={priority ? "eager" : "lazy"} onError={() => setFailed(safe)} className="object-cover" />
      : <div data-testid="cover-placeholder" aria-hidden="true" className="flex h-full w-full items-center justify-center text-text-secondary"><Icon name="gamepad" width={36} height={36} /></div>}
  </div>;
}
