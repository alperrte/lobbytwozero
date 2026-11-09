"use client";

import { useTranslations } from "next-intl";
import { cn } from "@/shared/utils/cn";
import type { GameActivity, GameVisual } from "../types/social.types";

const fillTone: Record<GameVisual["tone"], string> = {
  primary: "fill-primary",
  secondary: "fill-secondary",
  accent: "fill-accent"
};

const strokeTone: Record<GameVisual["tone"], string> = {
  primary: "stroke-primary",
  secondary: "stroke-secondary",
  accent: "stroke-accent"
};

function Glyph({ visual }: Readonly<{ visual: GameVisual }>) {
  const fill = fillTone[visual.tone];
  const stroke = strokeTone[visual.tone];
  switch (visual.glyph) {
    case "chevron":
      return <path className={fill} d="M10 14h10l10 10-10 10H10l10-10zM24 14h8l10 10-10 10h-8l10-10z" />;
    case "ring":
      return (
        <>
          <circle cx="24" cy="24" r="13" className={stroke} strokeWidth="4" fill="none" />
          <circle cx="24" cy="24" r="4" className={fill} />
        </>
      );
    case "diamond":
      return (
        <>
          <path className={fill} d="M24 8l14 16-14 16-14-16z" opacity="0.9" />
          <path className="fill-scene-ink" d="M24 16l7 8-7 8-7-8z" opacity="0.55" />
        </>
      );
    case "bolt":
      return <path className={fill} d="M27 6 12 27h10l-3 15 17-23H26z" />;
    case "grid":
      return (
        <path
          className={stroke}
          strokeWidth="3"
          fill="none"
          d="M8 34h32M12 26h24M16 18h16M24 10v28M14 38l6-24M34 38l-6-24"
        />
      );
  }
}

/**
 * Oyun kapağı YER TUTUCUSU. Gerçek kapak görselleri game-service'ten lisanslı kaynakla gelecektir (web-rules §3.1); o zamana kadar
 * oyunun marka tonunda geometrik bir desen çizilir. Ad her zaman yanında metin olarak bulunduğundan görsel dekoratiftir.
 */
export function GameCover({ visual, className }: Readonly<{ visual: GameVisual; className?: string }>) {
  return (
    <span
      aria-hidden="true"
      className={cn("inline-flex shrink-0 overflow-hidden rounded-control border border-border bg-surface-elevated", className)}
    >
      <svg viewBox="0 0 48 48" className="size-full" focusable="false">
        <rect width="48" height="48" className={fillTone[visual.tone]} opacity="0.14" />
        <Glyph visual={visual} />
      </svg>
    </span>
  );
}

/** Kartın sağ üstündeki büyük, solgun desen (taslaktaki oyun görseli alanının yerine). */
export function GameBackdrop({ visual }: Readonly<{ visual: GameVisual }>) {
  return (
    <svg
      viewBox="0 0 48 48"
      aria-hidden="true"
      focusable="false"
      className="pointer-events-none absolute -end-6 -top-8 size-44 opacity-25 [mask-image:linear-gradient(to_left,black,transparent)]"
    >
      <Glyph visual={visual} />
    </svg>
  );
}

/** Kapak + oyun adı + etkinlik ("Dereceli oynuyor"). Oyun yoksa sade bir satır. */
/**
 * Oyun satırı. `lastPlayed` (kişi çevrimdışı) iken oyun şu an oynanmıyordur: etkinlik ("Arkadaşıyla oynuyor") yerine
 * "En son oynadığı" yazılır.
 */
export function GameLine({
  game,
  size = "md",
  lastPlayed = false
}: Readonly<{ game: GameActivity | null; size?: "sm" | "md"; lastPlayed?: boolean }>) {
  const t = useTranslations("Social");
  if (!game) {
    return <p className="text-sm text-text-secondary">{t("game.none")}</p>;
  }
  return (
    <div className="flex min-w-0 items-center gap-3">
      <GameCover visual={game.visual} className={size === "md" ? "size-11" : "size-9"} />
      <div className="min-w-0">
        <p className="truncate text-sm font-semibold text-text-primary">{game.title}</p>
        <p className="truncate text-xs text-text-secondary">
          {lastPlayed ? t("game.lastPlayed") : t(`activity.${game.activity}`)}
        </p>
      </div>
    </div>
  );
}
