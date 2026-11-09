"use client";

import { useId, useState, type FormEvent } from "react";
import { useTranslations } from "next-intl";
import { Link } from "@/shared/lib/i18n/navigation";
import { useGameResource } from "../hooks/use-game-resource";
import type { CatalogSearchPage } from "../types/game";
import { coverOf, GameCover } from "./game-cover";
import { controlClass, GameStatus } from "./game-status";
import { Badge, checkboxClass, EmptyState, Pagination, primaryButtonClass, Skeleton } from "./game-ui";

type Submitted = { q: string; includeUnclassified: boolean };

function Results({ submitted, page, onPage }: { submitted: Submitted; page: number; onPage: (page: number) => void }) {
  const t = useTranslations("Game");
  const result = useGameResource<CatalogSearchPage>({ kind: "catalogSearch", q: submitted.q, page, includeUnclassified: submitted.includeUnclassified }, { refreshOnFocus: false });
  return <div className="space-y-4">
    {result.loading && <Skeleton className="h-24" />}
    <GameStatus error={result.error} loading={false} onRetry={result.reload} />
    {result.data && (!result.data.items.length
      ? <EmptyState title={t("indexNoResults")} />
      : <>
        <p role="status" className="text-sm text-text-secondary">{t("total", { count: result.data.totalElements })}</p>
        <ul className="space-y-2">{result.data.items.map((game) => <li key={game.gameId} className="flex flex-wrap items-center gap-3 rounded-panel border border-border bg-surface p-2">
          <GameCover url={coverOf(game)} title={game.title} className="w-24 shrink-0 rounded-control" />
          <div className="min-w-0 flex-1"><p className="font-medium [overflow-wrap:anywhere]">{game.title}</p>
            <p className="mt-1 flex flex-wrap gap-2">{game.gameType && <Badge>{t.has(`choices.${game.gameType}`) ? t(`choices.${game.gameType}`) : t("unknown")}</Badge>}<Badge>{t.has(`coverage.${game.metadataCompleteness}`) ? t(`coverage.${game.metadataCompleteness}`) : t("unknown")}</Badge></p></div>
          <Link href={{ pathname: "/games/manage", query: { section: "admin", gameId: game.gameId } }} className={`${controlClass} inline-flex items-center`}>{t("openInAdmin")}</Link>
        </li>)}</ul>
        <Pagination page={page} size={20} totalElements={result.data.totalElements} onChange={onPage} />
      </>)}
  </div>;
}

/**
 * Yerel Steam indeksinde (GET /api/v1/game/catalog) önek araması. Sağlayıcıya istek atılmaz; sınıflandırılmamış kayıtlar
 * isteğe bağlı dahil edilir ve yönetimde açılabilir. Bu uç herkese açık katalog listesinin yerine geçmez.
 */
export function GameIndexSearch() {
  const t = useTranslations("Game");
  const id = useId();
  const [draft, setDraft] = useState("");
  const [include, setInclude] = useState(false);
  const [submitted, setSubmitted] = useState<Submitted>();
  const [page, setPage] = useState(0);
  function submit(event: FormEvent) {
    event.preventDefault();
    const q = draft.trim();
    if (!q) return;
    setSubmitted({ q, includeUnclassified: include });
    setPage(0);
  }
  return <section aria-labelledby={`${id}-title`} className="space-y-4 rounded-panel border border-border bg-surface p-5">
    <h2 id={`${id}-title`} className="font-display text-lg font-semibold">{t("indexSearch")}</h2>
    <p className="max-w-prose text-sm text-text-secondary">{t("indexSearchHelp")}</p>
    <form onSubmit={submit} aria-label={t("indexSearch")} className="flex flex-wrap items-end gap-4">
      <label className="flex min-w-0 flex-1 basis-64 flex-col gap-2 text-sm">{t("search")}
        <input type="search" required maxLength={128} value={draft} onChange={(event) => setDraft(event.target.value)} className={controlClass} autoComplete="off" /></label>
      <label className="flex min-h-11 items-center gap-3 text-sm"><input type="checkbox" className={checkboxClass} checked={include} onChange={(event) => setInclude(event.target.checked)} />{t("includeUnclassified")}</label>
      <button type="submit" className={primaryButtonClass} disabled={!draft.trim()}>{t("indexSearchButton")}</button>
    </form>
    {submitted && <Results key={`${submitted.q}/${submitted.includeUnclassified}`} submitted={submitted} page={page} onPage={setPage} />}
  </section>;
}
