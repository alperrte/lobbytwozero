"use client";

import { useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { useLocale, useTranslations } from "next-intl";
import { Link, useRouter } from "@/shared/lib/i18n/navigation";
import { useGameResource } from "../hooks/use-game-resource";
import type { DiscoveryPage } from "../types/game";
import { controlClass, GameStatus } from "./game-status";
import { GameFilters } from "./game-filters";
import { discoveryQuery } from "../services/discovery-query";
import { GameSessionActions } from "./game-session-actions";

export function GameCatalogue() {
  const t = useTranslations("Game");
  const locale = useLocale();
  const params = useSearchParams();
  const router = useRouter();
  const query = params.get("q") ?? "";
  const [search, setSearch] = useState({ query, draft: query });
  if (search.query !== query) setSearch({ query, draft: query });
  const draft = search.query === query ? search.draft : query;
  const rawPage = Number(params.get("page") ?? 0);
  const page = Number.isInteger(rawPage) && rawPage >= 0 && rawPage <= 10000 ? rawPage : 0;
  const normalized = discoveryQuery(new URLSearchParams(params.toString()));
  normalized.set("page", String(page));
  const sort = normalized.get("sort")!;
  const result = useGameResource<DiscoveryPage>({ kind: "discovery", query: normalized.toString() });
  function navigate(changes: Record<string, string>) {
    const next = new URLSearchParams(params.toString());
    for (const [key, value] of Object.entries(changes)) {
      if (value) next.set(key, value); else next.delete(key);
    }
    router.replace({ pathname: "/games", query: Object.fromEntries(next) }, { scroll: false });
  }
  useEffect(() => {
    if (draft === query) return;
    const timeout = setTimeout(() => {
      const next = new URLSearchParams(params.toString());
      if (draft) next.set("q", draft); else next.delete("q");
      next.delete("page");
      router.replace({ pathname: "/games", query: Object.fromEntries(next) }, { scroll: false });
    }, 300);
    return () => clearTimeout(timeout);
  }, [draft, query, params, router]);
  return <>
    <header className="mb-8"><h1 className="font-display text-3xl font-semibold sm:text-5xl">{t("catalogue")}</h1><p className="mt-4 text-text-secondary">{t("catalogueDescription")}</p></header>
    <div className="flex flex-wrap gap-4">
      <label className="flex min-w-0 flex-1 flex-col gap-2 text-sm">{t("search")}<input type="search" maxLength={128} value={draft} onChange={(event) => setSearch({ query, draft: event.target.value })} className={controlClass} /></label>
      <label className="flex flex-col gap-2 text-sm">{t("sort")}<select value={sort} onChange={(event) => navigate({ sort: event.target.value, page: "" })} className={controlClass}>
        <option value="TITLE">{t("sortTitle")}</option><option value="RELEASE_DATE">{t("releaseDate")}</option><option value="POPULARITY">{t("sortPopularity")}</option><option value="RATING">{t("rating")}</option>
      </select></label>
      <label className="flex flex-col gap-2 text-sm">{t("direction")}<select className={controlClass} value={normalized.get("direction")!} onChange={(event) => navigate({ direction: event.target.value, page: "" })}><option value="ASC">{t("ascending")}</option><option value="DESC">{t("descending")}</option></select></label>
    </div>
    <GameFilters query={params.toString()} onChange={navigate} />
    <GameSessionActions />
    <GameStatus {...result} onRetry={result.reload} />
    {result.data && <>
      <p className="mt-6 text-sm text-text-secondary">{t("total", { count: result.data.totalElements })}</p>
      {result.data.items.length === 0 ? <p className="py-12 text-text-secondary">{t("emptyCatalogue")}</p> : <ul className="mt-4 divide-y divide-border">
        {result.data.items.map(({ game, releaseDate, rating }) => <li key={game.gameId} className="flex flex-wrap items-baseline justify-between gap-3 py-5">
          <h2 className="min-w-0 break-words font-display text-xl font-semibold"><Link href={{ pathname: "/games/[gameId]", params: { gameId: game.gameId } }} className="inline-block py-2 text-brand-text underline-offset-4 hover:underline">{game.title}</Link></h2>
          <span className="text-sm text-text-secondary">{releaseDate || t("unknown")}</span>
          <span className="text-sm text-text-secondary">{rating?.status === "RATED" && rating.averageRating !== null ? t("ratingValue", { rating: new Intl.NumberFormat(locale, { maximumFractionDigits: 2 }).format(rating.averageRating), count: rating.ratingCount ?? 0 }) : t(rating?.status === "UNRATED" ? "unrated" : "ratingUnavailable")}</span>
        </li>)}
      </ul>}
      <nav aria-label={t("pagination")} className="mt-8 flex flex-wrap items-center gap-3">
        <button type="button" className={controlClass} disabled={page === 0} onClick={() => navigate({ page: String(page - 1) })}>{t("previous")}</button><span>{t("page", { page: page + 1 })}</span>
        <button type="button" className={controlClass} disabled={(page + 1) * 20 >= result.data.totalElements || page >= 10000} onClick={() => navigate({ page: String(page + 1) })}>{t("next")}</button>
      </nav>
    </>}
  </>;
}
