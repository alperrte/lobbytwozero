"use client";

import { useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { Link } from "@/shared/lib/i18n/navigation";
import { useGameResource } from "../hooks/use-game-resource";
import type { GameDetail as Detail } from "../types/game";
import { GameCover } from "./game-cover";
import { GameDeveloperPublic } from "./game-developer-public";
import { GameMetadataDetails } from "./game-metadata-details";
import { GameModules } from "./game-modules";
import { GamePrices } from "./game-prices";
import { formatReleaseDate } from "./game-rating";
import { GameSessionActions } from "./game-session-actions";
import { GameStatus } from "./game-status";
import { Badge, Skeleton } from "./game-ui";

const sections = ["overview", "information", "prices", "developer", "community", "feedback"] as const;
const longDescription = 700;

function Description({ text }: { text: string | null }) {
  const t = useTranslations("Game");
  const [expanded, setExpanded] = useState(false);
  if (!text) return <p className="text-text-secondary">{t("missingDescription")}</p>;
  const long = text.length > longDescription;
  return <div>
    <div className={`max-w-prose whitespace-pre-wrap break-words leading-relaxed text-text-secondary ${long && !expanded ? "line-clamp-6" : ""}`}>{text}</div>
    {long && <button type="button" aria-expanded={expanded} onClick={() => setExpanded(!expanded)} className="mt-2 min-h-11 text-brand-text underline-offset-4 hover:underline">{t(expanded ? "showLess" : "showMore")}</button>}
  </div>;
}

function DetailSkeleton() {
  return <div aria-hidden="true" className="mt-6 grid gap-8 lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)]"><Skeleton className="aspect-video" /><div className="space-y-4"><Skeleton className="h-12 w-3/4" /><Skeleton className="h-5 w-1/2" /><Skeleton className="h-32" /></div></div>;
}

export function GameDetail({ gameId }: { gameId: string }) {
  const t = useTranslations("Game");
  const locale = useLocale();
  const result = useGameResource<Detail>({ kind: "detail", gameId });
  const detail = result.data;
  const coverage = detail && ["INDEX_ONLY", "PARTIAL", "FULL"].includes(detail.game.metadataCompleteness) ? detail.game.metadataCompleteness : "UNKNOWN";
  const date = formatReleaseDate(detail?.releaseDate, locale);
  const platforms = detail?.metadata.platforms.map((term) => locale === "tr" ? term.nameTr : term.nameEn).join(", ");
  return <div className="space-y-10">
    <Link href="/games" className="inline-flex min-h-11 items-center gap-2 text-brand-text underline-offset-4 hover:underline"><Icon name="arrowRight" width={16} height={16} className="rotate-180" />{t("backToGames")}</Link>
    {result.loading && <DetailSkeleton />}
    <GameStatus error={result.error} loading={false} onRetry={result.reload} />
    {detail && <>
      <header className="grid gap-8 lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)] lg:items-start">
        <GameCover url={detail.game.coverUrl} title={detail.game.title} priority className="rounded-panel border border-border" />
        <div className="min-w-0 space-y-4">
          <div className="flex flex-wrap items-center gap-2">
            {detail.game.gameType && <Badge>{t.has(`choices.${detail.game.gameType}`) ? t(`choices.${detail.game.gameType}`) : t("unknown")}</Badge>}
            {detail.metadata.earlyAccess === true && <Badge tone="warning">{t("earlyAccessBadge")}</Badge>}
            <Badge tone={coverage === "FULL" ? "success" : "neutral"}>{t(`coverage.${coverage}`)}</Badge>
          </div>
          <h1 className="font-display text-3xl font-semibold text-balance [overflow-wrap:anywhere] sm:text-5xl">{detail.game.title}</h1>
          <dl className="grid gap-4 text-sm sm:grid-cols-2">
            <div><dt className="text-text-secondary">{t("releaseDate")}</dt><dd className="mt-1">{date ? <time dateTime={detail.releaseDate ?? undefined}>{date}</time> : t("releaseUnknown")}</dd></div>
            <div><dt className="text-text-secondary">{t("developers")}</dt><dd className="mt-1 break-words">{detail.metadata.developers.join(", ") || t("unknown")}</dd></div>
            <div><dt className="text-text-secondary">{t("platforms")}</dt><dd className="mt-1 break-words">{platforms || t("unknown")}</dd></div>
          </dl>
        </div>
      </header>
      <nav aria-label={t("pageSections")} className="sticky top-[var(--spacing-header)] z-10 -mx-1 flex gap-1 overflow-x-auto border-b border-border bg-background/95 px-1 backdrop-blur [scrollbar-width:thin]">
        {sections.map((id) => <a key={id} href={`#game-${id}`} className="inline-flex min-h-11 shrink-0 items-center border-b-2 border-transparent px-4 text-sm font-semibold text-text-secondary hover:border-control-border hover:text-text-primary focus-visible:border-primary">{t(`sections.${id}`)}</a>)}
      </nav>
      <section id="game-overview" aria-labelledby="game-overview-title" className="scroll-mt-32 space-y-4">
        <h2 id="game-overview-title" className="font-display text-2xl font-semibold">{t("sections.overview")}</h2>
        <Description text={detail.description} />
        <p className="text-sm text-text-secondary">{t("descriptionSource")}</p>
      </section>
      <div id="game-information" className="scroll-mt-32"><GameMetadataDetails game={detail} /></div>
      <div id="game-prices" className="scroll-mt-32"><GamePrices gameId={gameId} /></div>
      <div id="game-developer" className="scroll-mt-32"><GameDeveloperPublic gameId={gameId} /></div>
      <div id="game-community" className="scroll-mt-32"><GameModules gameId={gameId} /></div>
      <div id="game-feedback" className="scroll-mt-32"><GameSessionActions gameId={gameId} earlyAccess={detail.metadata.earlyAccess === true} /></div>
    </>}
  </div>;
}
