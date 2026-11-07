"use client";

import { useLocale, useTranslations } from "next-intl";
import { Link } from "@/shared/lib/i18n/navigation";
import { useGameResource } from "../hooks/use-game-resource";
import type { GameDetail as Detail } from "../types/game";
import { GameModules } from "./game-modules";
import { GameStatus } from "./game-status";
import { GameMetadataDetails } from "./game-metadata-details";
import { GamePrices } from "./game-prices";
import { GameDeveloperPublic } from "./game-developer-public";
import { GameSessionActions } from "./game-session-actions";

export function GameDetail({ gameId }: { gameId: string }) {
  const t = useTranslations("Game");
  const locale = useLocale();
  const result = useGameResource<Detail>({ kind: "detail", gameId });
  const detail = result.data;
  const coverage = detail && ["INDEX_ONLY", "PARTIAL", "FULL"].includes(detail.game.metadataCompleteness) ? detail.game.metadataCompleteness : "UNKNOWN";
  return <>
    <Link href="/games" className="inline-block py-3 text-brand-text underline-offset-4 hover:underline">{t("backToGames")}</Link>
    <GameStatus {...result} onRetry={result.reload} />
    {detail && <>
      <header className="mt-5 space-y-4">
        <h1 className="font-display text-3xl font-semibold text-balance sm:text-5xl">{detail.game.title}</h1>
        <p className="text-sm text-text-secondary">{t("metadataStatus", { status: t(`coverage.${coverage}`) })}</p>
      </header>
      <div className="mt-8 grid gap-8 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
        <div className="max-w-prose whitespace-pre-wrap break-words leading-relaxed text-text-secondary">{detail.description || t("missingDescription")}</div>
        <dl className="space-y-4 border-t border-border pt-5 lg:border-t-0 lg:border-l lg:pl-6 lg:pt-0">
          <div><dt className="text-sm text-text-secondary">{t("releaseDate")}</dt><dd className="mt-1">{detail.releaseDate || t("unknown")}</dd></div>
          <div><dt className="text-sm text-text-secondary">{t("developers")}</dt><dd className="mt-1">{detail.metadata.developers.join(", ") || t("unknown")}</dd></div>
          <div><dt className="text-sm text-text-secondary">{t("platforms")}</dt><dd className="mt-1">{detail.metadata.platforms.map((term) => locale === "tr" ? term.nameTr : term.nameEn).join(", ") || t("unknown")}</dd></div>
        </dl>
      </div>
      <GameMetadataDetails game={detail} />
      <GamePrices gameId={gameId} />
      <GameDeveloperPublic gameId={gameId} />
      <GameModules gameId={gameId} />
      <GameSessionActions gameId={gameId} earlyAccess={detail.metadata.earlyAccess === true} />
    </>}
  </>;
}
