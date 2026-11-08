"use client";

import { useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { Link } from "@/shared/lib/i18n/navigation";
import { useGameResource } from "../hooks/use-game-resource";
import { DLC_PAGE_SIZE } from "../services/dlc";
import type { DlcPage, GameDetail } from "../types/game";
import { GameStatus } from "./game-status";
import { formatReleaseDate } from "./game-rating";
import { Pagination, Skeleton } from "./game-ui";

/** DLC detayında ana oyuna bağlantı. Ana oyun bilinmiyorsa (relation.parent yok) hiçbir bağlantı uydurulmaz. */
export function GameParentLink({ detail }: { detail: GameDetail }) {
  const t = useTranslations("Game");
  const parent = detail.game.gameType === "DLC" ? detail.relation?.parent : null;
  if (!parent) return null;
  return <div><dt className="text-text-secondary">{t("parentGame")}</dt><dd className="mt-1 break-words">
    <Link href={{ pathname: "/games/[gameId]", params: { gameId: parent.gameId } }} className="text-brand-text underline-offset-4 hover:underline">{parent.title}</Link>
  </dd></div>;
}

/** Ana oyunun DLC'leri: ayrı bölüm, sayfalı, her biri kendi detayına bağlanır. DLC yoksa bölüm gösterilmez. */
export function GameDlcSection({ gameId, count }: { gameId: string; count: number }) {
  return count < 1 ? null : <DlcList gameId={gameId} count={count} />;
}

function DlcList({ gameId, count }: { gameId: string; count: number }) {
  const t = useTranslations("Game");
  const locale = useLocale();
  const [page, setPage] = useState(0);
  const result = useGameResource<DlcPage>({ kind: "dlc", gameId, page }, { refreshOnFocus: false });
  return <section id="game-dlc" aria-labelledby="game-dlc-title" className="scroll-mt-32 space-y-4">
    <h2 id="game-dlc-title" className="font-display text-2xl font-semibold">{t("dlcTitle", { count })}</h2>
    {result.loading && !result.data && <div aria-hidden="true" className="space-y-2"><Skeleton className="h-12" /><Skeleton className="h-12" /><Skeleton className="h-12" /></div>}
    <GameStatus error={result.error} loading={false} onRetry={result.reload} />
    {result.data && <>
      <ul className="divide-y divide-border rounded-panel border border-border">
        {result.data.items.map((item) => {
          const date = formatReleaseDate(item.releaseDate, locale);
          return <li key={item.game.gameId} className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 px-4 py-3">
            <Link href={{ pathname: "/games/[gameId]", params: { gameId: item.game.gameId } }} className="min-w-0 break-words text-brand-text underline-offset-4 hover:underline">{item.game.title}</Link>
            <span className="text-sm text-text-secondary">{date ? <time dateTime={item.releaseDate ?? undefined}>{date}</time> : t("releaseUnknown")}</span>
          </li>;
        })}
      </ul>
      <Pagination page={page} totalElements={result.data.totalElements} size={DLC_PAGE_SIZE} onChange={setPage} />
    </>}
  </section>;
}
