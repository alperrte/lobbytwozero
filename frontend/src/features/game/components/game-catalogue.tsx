"use client";

import { useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { useLocale, useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { useRouter } from "@/shared/lib/i18n/navigation";
import { useGameResource } from "../hooks/use-game-resource";
import { catalogueSize, lastPageIndex, sanitizedDiscoveryQuery } from "../services/discovery-query";
import type { CatalogStatus, DiscoveryPage } from "../types/game";
import { GameCardList, GameCardSkeletons, type CatalogueView } from "./game-card";
import { GameCatalogStatus } from "./game-catalog-status";
import { GameEditorsPicks } from "./game-editors-picks";
import { GameActiveFilters, GameFilters } from "./game-filters";
import { GameMyFeedback } from "./game-my-feedback";
import { GameSectionNav } from "./game-section-nav";
import { controlClass, GameStatus } from "./game-status";
import { cn } from "@/shared/utils/cn";
import { EmptyState, GameSelect, gameScopeClass, Pagination } from "./game-ui";

type CatalogueTab = "all" | "picks" | "feedback";
const tabIds: readonly CatalogueTab[] = ["all", "picks", "feedback"];
const clearAll = { q: "", genre: "", platform: "", language: "", earlyAccess: "", releasedAfter: "", releasedBefore: "", popularOnly: "", minRating: "", discountedOnly: "", page: "" };

function parsePage(value: string | null): number {
  const page = Number(value ?? 0);
  return Number.isInteger(page) && page >= 0 && page <= 10000 ? page : 0;
}

/**
 * Boş liste nedenleri ayrı anlatılır: arama, filtre, senkron sürüyor, indeks dolu ama sınıflandırılmamış, katalog tamamen boş.
 * Katalog durumu (herkese açık) yalnız filtresiz boş listede sorulur; alınamazsa genel boş mesajı gösterilir.
 */
function EmptyResults({ params, filtered, navigate }: { params: URLSearchParams; filtered: boolean; navigate: (changes: Record<string, string>) => void }) {
  const t = useTranslations("Game");
  const locale = useLocale();
  const status = useGameResource<CatalogStatus>({ kind: "catalogStatus" }, { refreshOnFocus: false });
  if (filtered) {
    const query = params.get("q") ?? "";
    const onlySearch = Boolean(query) && !["genre", "platform", "language", "earlyAccess", "releasedAfter", "releasedBefore", "popularOnly", "minRating", "discountedOnly"].some((key) => params.get(key));
    return <EmptyState title={onlySearch ? t("emptySearch", { query }) : t("emptyFilters")} action={<button type="button" className={controlClass} onClick={() => navigate(clearAll)}>{t("clearFilters")}</button>} />;
  }
  const data = status.data;
  if (!data) return <EmptyState title={t("emptyCatalogue")} />;
  const number = new Intl.NumberFormat(locale);
  const counts = { indexed: number.format(data.indexedEntries), main: number.format(data.mainGameEntries) };
  if (data.syncStatus === "RUNNING") return <EmptyState title={t("syncRunningHint", counts)} />;
  if (data.indexedEntries > 0) return <EmptyState title={t("emptyUnclassifiedHint", counts)} />;
  return <EmptyState title={t("emptyCatalogueAll")} />;
}

function Results({ params, view, page, navigate }: { params: URLSearchParams; view: CatalogueView; page: number; navigate: (changes: Record<string, string>) => void }) {
  const t = useTranslations("Game");
  const { query, errors } = sanitizedDiscoveryQuery(params);
  query.set("page", String(page));
  const result = useGameResource<DiscoveryPage>({ kind: "discovery", query: query.toString() });
  const filtered = ["q", "genre", "platform", "language", "earlyAccess", "releasedAfter", "releasedBefore", "popularOnly", "minRating", "discountedOnly"].some((key) => params.get(key));
  const data = result.data;
  return <>
    {errors.length > 0 && <p role="alert" className="mb-4 text-sm text-brand-text">{t(errors[0] === "dateRange" ? "filterErrors.dateRange" : "filterErrors.minRating")} {t("filterNotSent")}</p>}
    {result.loading && <GameCardSkeletons view={view} />}
    <GameStatus error={result.error} loading={false} onRetry={result.reload} />
    {data && data.items.length === 0 && page > 0 && data.totalElements > 0 && <EmptyState title={t("pageOutOfRange")} action={<button type="button" className={controlClass} onClick={() => navigate({ page: String(lastPageIndex(data.totalElements)) })}>{t("lastPage")}</button>} />}
    {data && !(data.items.length === 0 && page > 0 && data.totalElements > 0) && <>
      <p role="status" className="mb-4 text-sm text-text-secondary">{t("total", { count: data.totalElements })}</p>
      {data.items.length === 0
        ? <EmptyResults params={params} filtered={filtered} navigate={navigate} />
        : <GameCardList items={data.items} view={view} />}
      <Pagination page={page} size={catalogueSize} totalElements={data.totalElements} onChange={(next) => navigate({ page: next ? String(next) : "" })} />
    </>}
  </>;
}

export function GameCatalogue() {
  const t = useTranslations("Game");
  const params = useSearchParams();
  const router = useRouter();
  const urlParams = new URLSearchParams(params.toString());
  const query = params.get("q") ?? "";
  const [search, setSearch] = useState({ query, draft: query });
  if (search.query !== query) setSearch({ query, draft: query });
  const draft = search.query === query ? search.draft : query;
  const tab: CatalogueTab = tabIds.includes(params.get("tab") as CatalogueTab) ? (params.get("tab") as CatalogueTab) : "all";
  const view: CatalogueView = params.get("view") === "list" ? "list" : "grid";
  const page = parsePage(params.get("page"));
  const sortValue = ["TITLE", "RELEASE_DATE", "POPULARITY", "RATING"].includes(params.get("sort") ?? "") ? params.get("sort")! : "TITLE";
  const direction = params.get("direction") === "DESC" ? "DESC" : "ASC";

  function navigate(changes: Record<string, string>) {
    const next = new URLSearchParams(params.toString());
    for (const [key, value] of Object.entries(changes)) { if (value) next.set(key, value); else next.delete(key); }
    // Ayrık seçimler (filtre, sıra, sayfa, görünüm) geçmişe yazılır; geri/ileri tuşları adım adım gezer.
    router.push({ pathname: "/games", query: Object.fromEntries(next) }, { scroll: false });
  }
  useEffect(() => {
    if (draft === query) return;
    const timeout = setTimeout(() => {
      const next = new URLSearchParams(params.toString());
      if (draft.trim()) next.set("q", draft.trim()); else next.delete("q");
      next.delete("page");
      router.replace({ pathname: "/games", query: Object.fromEntries(next) }, { scroll: false });
    }, 300);
    return () => clearTimeout(timeout);
  }, [draft, query, params, router]);

  return <div className={cn("space-y-8", gameScopeClass)}>
    <header className="space-y-3">
      <h1 className="font-display text-3xl font-semibold text-balance sm:text-5xl">{t("catalogue")}</h1>
      <p className="max-w-prose text-text-secondary">{t("catalogueDescription")}</p>
    </header>
    <GameSectionNav current={tab} />
    <div className="space-y-6">
      {tab !== "feedback" && <div className="flex flex-wrap items-end gap-4">
        {tab === "all" && <label className="flex min-w-0 flex-1 basis-64 flex-col gap-2 text-sm">{t("search")}
          <span className="relative"><Icon name="search" width={18} height={18} className="pointer-events-none absolute start-3 top-1/2 -translate-y-1/2 text-text-secondary" />
            <input type="search" maxLength={128} value={draft} onChange={(event) => setSearch({ query, draft: event.target.value })} className={`${controlClass} w-full ps-10`} /></span>
        </label>}
        {tab === "all" && <>
          <label className="flex flex-col gap-2 text-sm">{t("sort")}<GameSelect value={sortValue} onChange={(event) => navigate({ sort: event.target.value === "TITLE" ? "" : event.target.value, page: "" })} className={controlClass}>
            <option value="TITLE">{t("sortTitle")}</option><option value="RELEASE_DATE">{t("releaseDate")}</option><option value="POPULARITY">{t("sortPopularity")}</option><option value="RATING">{t("rating")}</option></GameSelect></label>
          <label className="flex flex-col gap-2 text-sm">{t("direction")}<GameSelect className={controlClass} value={direction} onChange={(event) => navigate({ direction: event.target.value === "ASC" ? "" : event.target.value, page: "" })}><option value="ASC">{t("ascending")}</option><option value="DESC">{t("descending")}</option></GameSelect></label>
        </>}
        <div role="group" aria-label={t("viewMode")} className="flex gap-1">
          {(["grid", "list"] as const).map((value) => <button key={value} type="button" aria-pressed={view === value} onClick={() => navigate({ view: value === "grid" ? "" : value })} className={`${controlClass} aria-pressed:border-primary aria-pressed:text-brand-text`}>{t(value === "grid" ? "viewGrid" : "viewList")}</button>)}
        </div>
      </div>}
      {tab === "all" && <>
        <GameFilters params={urlParams} onApply={navigate} />
        <GameActiveFilters params={urlParams} onChange={navigate} />
        <Results params={urlParams} view={view} page={page} navigate={navigate} />
      </>}
      {tab === "picks" && <GameEditorsPicks page={page} view={view} onPage={(next) => navigate({ page: next ? String(next) : "" })} />}
      {tab === "feedback" && <GameMyFeedback />}
    </div>
    <GameCatalogStatus />
  </div>;
}
