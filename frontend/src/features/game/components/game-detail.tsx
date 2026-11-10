"use client";

import { useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { Link } from "@/shared/lib/i18n/navigation";
import { useGameResource } from "../hooks/use-game-resource";
import { termLabel } from "../services/format";
import type { GameDetail as Detail } from "../types/game";
import { coverOf, GameCover } from "./game-cover";
import { GameDlcSection, GameParentLink } from "./game-dlc";
import { GameDeveloperPublic } from "./game-developer-public";
import { GameMetadataDetails } from "./game-metadata-details";
import { GameModules } from "./game-modules";
import { GamePrices } from "./game-prices";
import { formatReleaseDate } from "./game-rating";
import { GameSectionNav } from "./game-section-nav";
import { GameSessionActions } from "./game-session-actions";
import { GameStatus } from "./game-status";
import { cn } from "@/shared/utils/cn";
import { Badge, gameScopeClass, Skeleton } from "./game-ui";

const baseSections = ["overview", "information", "prices", "developer", "community"] as const;
const longDescription = 700;

/** Açıklama dili arayüz dilinden farklıysa (ör. TR seçiliyken İngilizce kaynak) bunu belirtir; metin çevrilmez, dil özniteliği ekran okuyucuya doğru telaffuzu bildirir. */
function Description({ text, language }: { text: string | null; language?: string | null }) {
  const t = useTranslations("Game");
  const locale = useLocale();
  const [expanded, setExpanded] = useState(false);
  if (!text) return <p className="text-text-secondary">{t("missingDescription")}</p>;
  const long = text.length > longDescription;
  const code = language && /^[a-z]{2,3}(-[a-z0-9]{2,8})*$/.test(language) ? language : undefined;
  const foreign = code && code.split("-")[0] !== locale;
  let languageName = code;
  try { if (code) languageName = new Intl.DisplayNames(locale, { type: "language" }).of(code) ?? code; } catch { /* geçersiz kod: ham kod gösterilir */ }
  return <div>
    {foreign && <p className="mb-2 text-sm text-text-secondary">{t("descriptionLanguageNote", { language: languageName ?? "" })}</p>}
    <div lang={code} className={`max-w-prose whitespace-pre-wrap break-words leading-relaxed text-text-secondary ${long && !expanded ? "line-clamp-6" : ""}`}>{text}</div>
    {long && <button type="button" aria-expanded={expanded} onClick={() => setExpanded(!expanded)} className="mt-2 min-h-11 text-brand-text underline-offset-4 hover:underline">{t(expanded ? "showLess" : "showMore")}</button>}
  </div>;
}

function DetailSkeleton() {
  return <div aria-hidden="true" className="mt-6 grid gap-8 lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)]"><Skeleton className="aspect-video" /><div className="space-y-4"><Skeleton className="h-12 w-3/4" /><Skeleton className="h-5 w-1/2" /><Skeleton className="h-32" /></div></div>;
}

export function GameDetail({ gameId }: { gameId: string }) {
  const t = useTranslations("Game");
  const locale = useLocale();
  const result = useGameResource<Detail>({ kind: "detail", gameId, lang: locale });
  const detail = result.data;
  const coverage = detail && ["INDEX_ONLY", "PARTIAL", "FULL"].includes(detail.game.metadataCompleteness) ? detail.game.metadataCompleteness : "UNKNOWN";
  const date = formatReleaseDate(detail?.releaseDate, locale);
  const dlcCount = detail?.game.gameType === "MAIN_GAME" ? detail.relation?.dlcCount ?? 0 : 0;
  const sections = dlcCount > 0 ? ["overview", "information", "dlc", "prices", "developer", "community"] as const : baseSections;
  const platforms = detail?.metadata.platforms.map((term) => termLabel(term, locale)).filter(Boolean).join(", ");
  return <div className={cn("space-y-10", gameScopeClass)}>
    <GameSectionNav />
    <Link href="/games" className="inline-flex min-h-11 items-center gap-2 text-brand-text underline-offset-4 hover:underline"><Icon name="arrowRight" width={16} height={16} className="rotate-180" />{t("backToGames")}</Link>
    {result.loading && <DetailSkeleton />}
    <GameStatus error={result.error} loading={false} onRetry={result.reload} />
    {detail && <>
      <header className="grid gap-8 lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)] lg:items-start">
        <GameCover url={coverOf(detail.game)} title={detail.game.title} priority className="rounded-panel border border-border" />
        <div className="min-w-0 space-y-4">
          <div className="flex flex-wrap items-center gap-2">
            {detail.game.gameType && <Badge>{t.has(`choices.${detail.game.gameType}`) ? t(`choices.${detail.game.gameType}`) : t("unknown")}</Badge>}
            {detail.metadata.earlyAccess === true && <Badge tone="warning">{t("earlyAccessBadge")}</Badge>}
            {detail.metadata.free === true && <Badge tone="success">{t("freeBadge")}</Badge>}
            {detail.metadata.comingSoon === true && <Badge tone="brand">{t("comingSoonBadge")}</Badge>}
            {detail.metadata.adult === true && <Badge tone="warning">{t("adultBadge")}</Badge>}
            <Badge tone={coverage === "FULL" ? "success" : "neutral"}>{t(`coverage.${coverage}`)}</Badge>
          </div>
          <h1 className="font-display text-3xl font-semibold text-balance [overflow-wrap:anywhere] sm:text-5xl">{detail.game.title}</h1>
          {detail.metadata.delisted === true && <p role="note" data-testid="delisted-notice" className="rounded-panel border border-warning/60 bg-surface p-4 text-sm">{t("delistedNotice")}</p>}
          <dl className="grid gap-4 text-sm sm:grid-cols-2">
            <div><dt className="text-text-secondary">{t("releaseDate")}</dt><dd className="mt-1">{date ? <time dateTime={detail.releaseDate ?? undefined}>{date}</time> : t("releaseUnknown")}</dd></div>
            <div><dt className="text-text-secondary">{t("developers")}</dt><dd className="mt-1 break-words">{detail.metadata.developers.join(", ") || t("unknown")}</dd></div>
            <div><dt className="text-text-secondary">{t("platforms")}</dt><dd className="mt-1 break-words">{platforms || t("unknown")}</dd></div>
            <GameParentLink detail={detail} />
          </dl>
        </div>
      </header>
      <nav aria-label={t("pageSections")} className="-mx-1 flex gap-1 overflow-x-auto border-b border-border px-1 [scrollbar-width:thin]">
        {sections.map((id) => <a key={id} href={`#game-${id}`} className="inline-flex min-h-11 shrink-0 items-center border-b-2 border-transparent px-4 text-sm font-semibold text-text-secondary hover:border-control-border hover:text-text-primary focus-visible:border-primary">{t(`sections.${id}`)}</a>)}
      </nav>
      <section id="game-overview" aria-labelledby="game-overview-title" className="scroll-mt-32 space-y-4">
        <h2 id="game-overview-title" className="font-display text-2xl font-semibold">{t("sections.overview")}</h2>
        <Description text={detail.description} language={detail.descriptionLanguage} />
        <p className="text-sm text-text-secondary">{t("descriptionSource")}</p>
      </section>
      <div id="game-information" className="scroll-mt-32"><GameMetadataDetails game={detail} /></div>
      {dlcCount > 0 && <GameDlcSection gameId={gameId} count={dlcCount} />}
      <div id="game-prices" className="scroll-mt-32"><GamePrices gameId={gameId} free={detail?.metadata.free} comingSoon={detail?.metadata.comingSoon} /></div>
      <div id="game-developer" className="scroll-mt-32"><GameDeveloperPublic gameId={gameId} /></div>
      <div id="game-community" className="scroll-mt-32"><GameModules gameId={gameId} /></div>
      <GameSessionActions gameId={gameId} />
    </>}
  </div>;
}
