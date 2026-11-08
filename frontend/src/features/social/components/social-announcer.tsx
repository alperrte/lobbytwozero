"use client";

import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { Icon } from "@/shared/components/ui/icons";
import { cn } from "@/shared/utils/cn";

type Announcement = { id: number; text: string; tone: "success" | "error" };

type Announce = (text: string, tone?: Announcement["tone"]) => void;

type AnnouncerValue = { announce: Announce; clear: () => void };

const AnnouncerContext = createContext<AnnouncerValue>({ announce: () => undefined, clear: () => undefined });

/** Sosyal işlemlerin sonucunu duyurur (kabul edildi, engellendi…). Bileşenler `useAnnounce()` ile çağırır. */
export function useAnnounce(): Announce {
  return useContext(AnnouncerContext).announce;
}

/**
 * Görünen bandı hemen kapatır. Hatasını kendi alanında gösteren bileşen (davet koduyla ekleme) bunu çağırır; yoksa önceki
 * "gönderildi" bandı yeni hatanın yanında birkaç saniye daha kalır ve iki çelişkili mesaj aynı anda görünür.
 */
export function useClearAnnouncement(): () => void {
  return useContext(AnnouncerContext).clear;
}

const VISIBLE_MS = 7000;

/**
 * İşlem sonucu bandı. Başarı `role="status"` (kibar), hata `role="alert"` ile ekran okuyucuya duyurulur. Band birkaç saniye
 * görünür kalır; sonuç zaten listede de görünür olduğundan kritik bilgi yalnız burada kalmaz.
 */
export function SocialAnnouncerProvider({ children }: { children: ReactNode }) {
  const [current, setCurrent] = useState<Announcement | null>(null);

  useEffect(() => {
    if (!current) {
      return;
    }
    const timeout = setTimeout(() => setCurrent(null), VISIBLE_MS);
    return () => clearTimeout(timeout);
  }, [current]);

  // Yalnız (sabit) setCurrent'a dayanır; bir kez kurulur, tüketiciler her duyuruda yeniden çizilmez.
  const [value] = useState<AnnouncerValue>(() => ({
    announce: (text, tone = "success") => setCurrent({ id: Date.now(), text, tone }),
    clear: () => setCurrent(null)
  }));

  return (
    <AnnouncerContext.Provider value={value}>
      {children}
      <div className="pointer-events-none fixed inset-x-0 bottom-4 z-40 flex justify-center px-4">
        <div role="status" aria-live="polite" className="contents">
          {current?.tone === "success" ? <Banner key={current.id} {...current} /> : null}
        </div>
        <div role="alert" className="contents">
          {current?.tone === "error" ? <Banner key={current.id} {...current} /> : null}
        </div>
      </div>
    </AnnouncerContext.Provider>
  );
}

function Banner({ text, tone }: Announcement) {
  return (
    <p
      className={cn(
        "pointer-events-auto flex max-w-lg items-center gap-2.5 rounded-panel border bg-surface px-4 py-3 text-sm font-medium text-text-primary shadow-popover animate-pop-in",
        tone === "success" ? "border-success/50" : "border-primary/60"
      )}
    >
      <Icon
        name={tone === "success" ? "check" : "info"}
        className={cn("size-5 shrink-0", tone === "success" ? "text-success" : "text-brand-text")}
      />
      {text}
    </p>
  );
}
