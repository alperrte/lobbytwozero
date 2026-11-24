"use client";

import { useId } from "react";
import { useTranslations } from "next-intl";
import { GameCover, coverOf } from "@/features/game/components/game-cover";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { Link } from "@/shared/lib/i18n/navigation";
import { cn } from "@/shared/utils/cn";
import type { CompatibleGame, GameCard } from "../../types/recommendation";
import { RankBadge, panelClass } from "../recommendation-ui";
import { StatusChip } from "./assessment-result";

/**
 * Donanıma uygun oyun kartı. `MEETS_MINIMUM_COMPUTE` yalnız "minimum CPU/GPU/RAM beyanı karşılanıyor" demektir (oynanabilirlik
 * garantisi değildir); `UNKNOWN` nötr "doğrulanamadı" olarak gösterilir, hiçbir zaman uyumlu/yeşil değildir.
 */
export function CompatibleGameCard({ item, rank, card, available, genreLabel, onAssess }: {
  item: CompatibleGame;
  rank: number;
  card: GameCard | null;
  available: boolean;
  genreLabel: (code: string) => string;
  onAssess: () => void;
}) {
  const t = useTranslations("Recommendation.hardware");
  const titleId = useId();
  const title = card?.title ?? t("unknownTitle");
  const meets = item.compatibility === "MEETS_MINIMUM_COMPUTE";
  const matched = item.matchedGenres.map(genreLabel);
  return <article aria-labelledby={titleId} className={cn(panelClass, "flex min-w-0 flex-col overflow-hidden")}>
    <GameCover url={card ? coverOf(card) : null} className="border-b border-border">
      <span className="absolute start-3 top-3"><RankBadge rank={rank} /></span>
    </GameCover>
    <div className="flex min-w-0 flex-1 flex-col gap-3 p-4">
      <h3 id={titleId} className="line-clamp-2 font-display text-lg font-semibold leading-snug">
        {card && available
          ? <Link href={{ pathname: "/games/[gameId]", params: { gameId: item.gameId } }} className="rounded-sm hover:text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus">{title}</Link>
          : title}
      </h3>
      {!available ? <p className="text-sm text-text-secondary">{t("unavailable")}</p> : null}
      <div className="space-y-1.5">
        <StatusChip tone={meets ? "ok" : "neutral"}>{t(`compatibility.${item.compatibility}`)}</StatusChip>
        <p className="text-xs leading-5 text-text-secondary">{t(`compatibilityReason.${item.reason}`)}</p>
      </div>
      {matched.length ? <p className="text-sm leading-6 text-text-secondary"><span className="font-semibold text-text-primary">{t("why")}</span>{" "}{t("reason", { genres: matched.join(", ") })}</p> : null}
      <div className="mt-auto pt-1">
        <button type="button" className={buttonClass("secondary", "sm", "w-full")} aria-haspopup="dialog" onClick={onAssess}>{t("assess")}</button>
      </div>
    </div>
  </article>;
}
