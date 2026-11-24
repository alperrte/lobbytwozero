"use client";

import { useId, type ReactNode } from "react";
import { useTranslations } from "next-intl";
import { GameCover, coverOf } from "@/features/game/components/game-cover";
import { Link } from "@/shared/lib/i18n/navigation";
import { cn } from "@/shared/utils/cn";
import type { RecommendedGameView } from "../../services/adapters";
import { panelClass, RankBadge } from "../recommendation-ui";

/**
 * Önerilen oyun kartı. Gösterilen: sıra, kapak, ad, türler ve "neden" açıklaması (eşleşen türler).
 * Skor bir yüzde/olasılık olmadığından gösterilmez. Oyun kataloğda bulunamazsa kart düşürülmez; durum açıkça yazılır.
 */
export function GameRecommendationCard({ view, genreLabel, footer }: { view: RecommendedGameView; genreLabel: (code: string) => string; footer?: ReactNode }) {
  const t = useTranslations("Recommendation.games");
  const titleId = useId();
  const { item, card, metadata, available } = view;
  const title = card?.title ?? t("unknownTitle");
  const matched = item.matchedGenres.map(genreLabel);
  const genres = (metadata?.genres ?? []).filter((code) => !item.matchedGenres.includes(code)).slice(0, 3).map(genreLabel);
  return <article aria-labelledby={titleId} className={cn(panelClass, "group relative flex min-w-0 flex-col overflow-hidden transition-colors duration-150 hover:border-primary/45 motion-reduce:transition-none")}>
    <GameCover url={card ? coverOf(card) : null} className="border-b border-border">
      <span className="absolute start-3 top-3"><RankBadge rank={item.rank} /></span>
    </GameCover>
    <div className="flex min-w-0 flex-1 flex-col gap-3 p-4">
      <h3 id={titleId} className="line-clamp-2 font-display text-lg font-semibold leading-snug text-text-primary">
        {card && available
          ? <Link href={{ pathname: "/games/[gameId]", params: { gameId: item.gameId } }} className="rounded-sm hover:text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus">{title}</Link>
          : title}
      </h3>
      {!available ? <p className="text-sm text-text-secondary">{t("unavailable")}</p> : null}
      {matched.length
        ? <p className="text-sm leading-6 text-text-secondary"><span className="font-semibold text-text-primary">{t("why")}</span>{" "}{t("reason", { genres: matched.join(", ") })}</p>
        : <p className="text-sm leading-6 text-text-secondary">{t("reasonGeneric")}</p>}
      {genres.length ? <p className="text-xs text-text-secondary">{t("alsoGenres", { genres: genres.join(", ") })}</p> : null}
      {footer ? <div className="mt-auto pt-1">{footer}</div> : null}
    </div>
  </article>;
}
