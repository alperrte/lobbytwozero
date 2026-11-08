"use client";

import { useLocale, useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { Link } from "@/shared/lib/i18n/navigation";
import { cn } from "@/shared/utils/cn";
import type { DiscoveryItem } from "../types/game";
import { coverOf, GameCover } from "./game-cover";
import { formatReleaseDate, GameRatingText } from "./game-rating";
import { Badge } from "./game-ui";

export type CatalogueView = "grid" | "list";

/** Katalog kartı. Tek bağlantı başlıktadır; kartın tamamı bu bağlantıya genişletilir. Her kart için ek API çağrısı yoktur. */
export function GameCard({ item, view, rank }: { item: DiscoveryItem; view: CatalogueView; rank?: number | null }) {
  const t = useTranslations("Game");
  const locale = useLocale();
  const { game } = item;
  const date = formatReleaseDate(item.releaseDate, locale);
  const coverage = ["INDEX_ONLY", "PARTIAL", "FULL"].includes(game.metadataCompleteness) ? game.metadataCompleteness : "UNKNOWN";
  const list = view === "list";
  return <li className={cn("group relative overflow-hidden rounded-panel border border-border bg-surface transition-[border-color,transform,box-shadow] motion-safe:duration-200 hover:border-control-border focus-within:border-primary has-[a:focus-visible]:outline-2 has-[a:focus-visible]:outline-offset-2 has-[a:focus-visible]:outline-focus motion-safe:hover:-translate-y-0.5", list ? "flex flex-col sm:flex-row" : "flex flex-col")}>
    <GameCover url={coverOf(game)} title={game.title} className={list ? "sm:w-56 sm:shrink-0" : ""}>
      {item.earlyAccess === true && <span data-testid="early-access-mark" className="absolute end-2 top-2 inline-flex items-center gap-1 rounded-full border border-warning/60 bg-background/85 px-2.5 py-1 text-xs font-semibold text-warning backdrop-blur"><Icon name="sparkles" width={12} height={12} aria-hidden="true" />{t("earlyAccessBadge")}</span>}
    </GameCover>
    <div className="flex min-w-0 flex-1 flex-col gap-3 p-4">
      <div className="flex flex-wrap items-center gap-2">
        {rank != null && <Badge tone="brand">{t("editorRank", { rank })}</Badge>}
        {game.gameType && game.gameType !== "MAIN_GAME" && <Badge>{t.has(`choices.${game.gameType}`) ? t(`choices.${game.gameType}`) : t("unknown")}</Badge>}
      </div>
      <h2 className="font-display text-lg font-semibold leading-snug text-balance [overflow-wrap:anywhere]">
        <Link href={{ pathname: "/games/[gameId]", params: { gameId: game.gameId } }} className="after:absolute after:inset-0 after:content-[''] focus-visible:outline-none hover:text-brand-text">{game.title}</Link>
      </h2>
      <dl className="mt-auto flex flex-wrap items-center justify-between gap-x-4 gap-y-2 text-sm text-text-secondary">
        <div><dt className="sr-only">{t("releaseDate")}</dt><dd>{date ? <time dateTime={item.releaseDate ?? undefined}>{date}</time> : t("releaseUnknown")}</dd></div>
        <div><dt className="sr-only">{t("rating")}</dt><dd><GameRatingText rating={item.rating} compact /></dd></div>
        {list && <div><dt className="sr-only">{t("coverageLabel")}</dt><dd>{t(`coverage.${coverage}`)}</dd></div>}
      </dl>
    </div>
  </li>;
}

export function GameCardSkeletons({ view, count = 8 }: { view: CatalogueView; count?: number }) {
  return <ul aria-hidden="true" className={cn("grid gap-5", view === "grid" ? "grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-4" : "grid-cols-1")}>
    {Array.from({ length: count }, (_, index) => <li key={index} className={cn("overflow-hidden rounded-panel border border-border bg-surface", view === "list" ? "flex flex-col sm:flex-row" : "")}>
      <div className={cn("aspect-video bg-surface-elevated motion-safe:animate-pulse", view === "list" ? "sm:w-56 sm:shrink-0" : "w-full")} />
      <div className="flex-1 space-y-3 p-4"><div className="h-5 w-3/4 rounded-control bg-surface-elevated motion-safe:animate-pulse" /><div className="h-4 w-1/2 rounded-control bg-surface-elevated motion-safe:animate-pulse" /></div>
    </li>)}
  </ul>;
}

export function GameCardList({ items, view, ranked = false }: { items: DiscoveryItem[]; view: CatalogueView; ranked?: boolean }) {
  return <ul className={cn("grid gap-5", view === "grid" ? "grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-4" : "grid-cols-1")}>
    {items.map((item) => <GameCard key={item.game.gameId} item={item} view={view} rank={ranked ? item.editorialRank : null} />)}
  </ul>;
}
