"use client";

import { useFormatter, useNow, useTranslations } from "next-intl";
import { cn } from "@/shared/utils/cn";
import type { Presence } from "../types/social.types";

const dotClass: Record<Presence["status"], string> = {
  online: "bg-success",
  inGame: "bg-warning",
  offline: "bg-text-secondary/60"
};

const textClass: Record<Presence["status"], string> = {
  online: "text-success",
  inGame: "text-warning",
  offline: "text-text-secondary"
};

/**
 * Çevrimiçi / Oyunda / Çevrimdışı durumu. Renk tek bilgi kanalı değildir: nokta yanında her zaman metin vardır (web-rules §6).
 * Çevrimdışıysa son görülme göreli zamanla yazılır.
 */
export function PresenceLabel({ presence, className }: { presence: Presence; className?: string }) {
  const t = useTranslations("Social.presence");
  const format = useFormatter();
  const now = useNow({ updateInterval: 60_000 });
  const lastSeen =
    presence.status === "offline" && presence.lastSeenAt
      ? t("lastSeen", { time: format.relativeTime(new Date(presence.lastSeenAt), now) })
      : null;

  return (
    <p className={cn("flex flex-wrap items-center gap-x-2 gap-y-0.5 text-sm font-medium", textClass[presence.status], className)}>
      <span aria-hidden="true" className={cn("size-2.5 shrink-0 rounded-full", dotClass[presence.status])} />
      <span>{t(presence.status)}</span>
      {lastSeen ? <span className="basis-full ps-[1.125rem] text-xs font-normal text-text-secondary">{lastSeen}</span> : null}
    </p>
  );
}
