"use client";

import { useTranslations } from "next-intl";
import { useGameResource } from "../hooks/use-game-resource";
import type { DiscoveryPage } from "../types/game";
import { GameCardList, GameCardSkeletons, type CatalogueView } from "./game-card";
import { GameStatus } from "./game-status";
import { catalogueSize } from "../services/discovery-query";
import { EmptyState, Pagination } from "./game-ui";

/** Editörün Seçtikleri: editör sırası. Kullanıcı etkileşimine dayalı gerçek zamanlı trend değildir. */
export function GameEditorsPicks({ page, view, onPage }: { page: number; view: CatalogueView; onPage: (page: number) => void }) {
  const t = useTranslations("Game");
  const result = useGameResource<DiscoveryPage>({ kind: "popular", page, size: catalogueSize });
  return <section aria-labelledby="game-picks-title">
    <h2 id="game-picks-title" className="font-display text-2xl font-semibold">{t("editorsPicks")}</h2>
    <p className="mt-2 max-w-prose text-sm text-text-secondary">{t("editorsPicksHelp")}</p>
    <div className="mt-6">
      {result.loading && <GameCardSkeletons view={view} count={4} />}
      <GameStatus error={result.error} loading={false} onRetry={result.reload} />
      {result.data && (result.data.items.length === 0
        ? <EmptyState title={t("emptyPicks")}>{t("emptyPicksHelp")}</EmptyState>
        : <><p className="mb-4 text-sm text-text-secondary">{t("total", { count: result.data.totalElements })}</p><GameCardList items={result.data.items} view={view} ranked /><Pagination page={page} size={catalogueSize} totalElements={result.data.totalElements} onChange={onPage} /></>)}
    </div>
  </section>;
}
