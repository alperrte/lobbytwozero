"use client";

import { useId, useState, type FormEvent } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useLocale, useTranslations } from "next-intl";
import { Link } from "@/shared/lib/i18n/navigation";
import { useReviewResource } from "../hooks/use-review-resource";
import { HUB_PAGE_SIZE, HUB_QUERY_MAX, hubQueryFrom } from "../schemas/review-schema";
import { formatAverage, formatCount, safeCoverUrl } from "../services/format";
import type { RatingSummariesResponse, RatingSummary, ReviewGameSearchItem, ReviewGameSearchPage } from "../types/review";
import { ReviewBoundary } from "./review-boundary";
import { EmptyState, Pagination, Panel, ReviewAlert, ReviewCover, Skeleton, actionClass, focusRing } from "./review-ui";

/**
 * Review merkezi. Review'da "tüm incelemeler" veya "incelemelerim" akışı uç noktası olmadığından burada sahte bir akış YOKTUR:
 * gerçek Game kataloğundan oyun aranır/seçilir ve incelemelere oyun üzerinden ulaşılır. Oyun kartlarındaki LTZ puanı,
 * sayfa başına TEK toplu istekle (REV-12) Review Service'ten okunur.
 */
export function ReviewHub() {
  return (
    <ReviewBoundary>
      <HubWorkspace />
    </ReviewBoundary>
  );
}

const concepts = ["rating", "recommendation", "review"] as const;
const capabilities = ["rate", "recommend", "write", "like", "statistics"] as const;

function HubWorkspace() {
  const t = useTranslations("Review");
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();
  const { q, page } = hubQueryFrom(new URLSearchParams(params.toString()));
  const games = useReviewResource<ReviewGameSearchPage>({ kind: "gameSearch", q, page, size: HUB_PAGE_SIZE });
  const ids = games.data?.items.map((item) => item.game.gameId) ?? [];
  const summaries = useReviewResource<RatingSummariesResponse>(ids.length > 0 ? { kind: "ratingSummaries", gameIds: ids } : null);

  function go(next: { q: string; page: number }) {
    const search = new URLSearchParams();
    if (next.q) search.set("q", next.q);
    if (next.page > 0) search.set("page", String(next.page));
    const text = search.toString();
    router.push(text ? `${pathname}?${text}` : pathname, { scroll: false });
  }

  const byGame = new Map<string, RatingSummary>((summaries.data?.items ?? []).map((item) => [item.gameId.toLowerCase(), item]));
  const titleId = useId();

  return (
    <>
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div className="space-y-2">
          <h1 className="font-display text-3xl font-semibold text-balance sm:text-4xl">{t("hub.title")}</h1>
          <p className="max-w-prose text-base leading-7 text-text-secondary">{t("hub.description")}</p>
        </div>
        <Link href="/reviews/mine" className={actionClass("secondary")}>{t("hub.mine")}</Link>
      </header>

      <section aria-label={t("hub.conceptsTitle")} className="grid gap-3 md:grid-cols-3">
        {concepts.map((concept) => (
          <div key={concept} className="space-y-1 rounded-panel border border-border bg-surface p-4">
            <h2 className="font-display text-base font-semibold">{t(`hub.concepts.${concept}.title`)}</h2>
            <p className="text-sm leading-6 text-text-secondary">{t(`hub.concepts.${concept}.text`)}</p>
          </div>
        ))}
      </section>

      <Panel title={t("hub.findTitle")} titleId={titleId} description={t("hub.findDescription")}>
        <div className="space-y-5">
          <SearchForm key={q} initial={q} onSearch={(value) => go({ q: value, page: 0 })} />
          <ReviewAlert error={games.error} onRetry={games.reload} />
          {games.loading && !games.data ? (
            <div role="status" aria-label={t("loading")} className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
              <span className="sr-only">{t("loading")}</span>
              {[0, 1, 2].map((key) => <Skeleton key={key} className="h-56" />)}
            </div>
          ) : null}
          {games.data ? (
            <ResultList
              data={games.data}
              query={q}
              summaries={byGame}
              summariesLoading={summaries.loading}
              summariesFailed={Boolean(summaries.error)}
              onRetrySummaries={summaries.reload}
              onPage={(next) => go({ q, page: next })}
              onClear={() => go({ q: "", page: 0 })}
            />
          ) : null}
        </div>
      </Panel>

      <section aria-label={t("hub.capabilitiesTitle")} className="grid gap-4 lg:grid-cols-2">
        <div className="space-y-3 rounded-panel border border-border bg-surface p-5">
          <h2 className="font-display text-lg font-semibold">{t("hub.capabilitiesTitle")}</h2>
          <ul className="list-disc space-y-1 ps-5 text-sm leading-6 text-text-secondary">
            {capabilities.map((capability) => <li key={capability}>{t(`hub.capabilities.${capability}`)}</li>)}
          </ul>
        </div>
        <div className="space-y-3 rounded-panel border border-border bg-surface p-5">
          <h2 className="font-display text-lg font-semibold">{t("hub.limitsTitle")}</h2>
          <p className="text-sm leading-6 text-text-secondary">{t("hub.limitsText")}</p>
        </div>
      </section>
    </>
  );
}

function SearchForm({ initial, onSearch }: { initial: string; onSearch: (q: string) => void }) {
  const t = useTranslations("Review");
  const id = useId();
  const [draft, setDraft] = useState(initial);
  function submit(event: FormEvent) {
    event.preventDefault();
    onSearch(draft.replace(/\s+/g, " ").trim().slice(0, HUB_QUERY_MAX));
  }
  return (
    <form role="search" onSubmit={submit} aria-label={t("hub.searchLabel")} className="flex flex-wrap items-end gap-3">
      <div className="min-w-0 flex-1 basis-64 space-y-1">
        <label htmlFor={`${id}-q`} className="block text-sm font-semibold">{t("hub.searchLabel")}</label>
        <input
          id={`${id}-q`}
          type="search"
          value={draft}
          maxLength={HUB_QUERY_MAX}
          autoComplete="off"
          placeholder={t("hub.searchPlaceholder")}
          onChange={(event) => setDraft(event.target.value)}
          className={`block min-h-11 w-full rounded-control border border-control-border bg-surface-elevated px-3 text-sm text-text-primary placeholder:text-text-secondary ${focusRing}`}
        />
      </div>
      <button type="submit" className={actionClass("primary")}>{t("hub.searchButton")}</button>
    </form>
  );
}

function ResultList({ data, query, summaries, summariesLoading, summariesFailed, onRetrySummaries, onPage, onClear }: { data: ReviewGameSearchPage; query: string; summaries: Map<string, RatingSummary>; summariesLoading: boolean; summariesFailed: boolean; onRetrySummaries: () => void; onPage: (page: number) => void; onClear: () => void }) {
  const t = useTranslations("Review");
  const locale = useLocale();
  if (data.items.length === 0) {
    return (
      <EmptyState
        title={query ? t("hub.noResults", { query }) : t("hub.noGames")}
        hint={query ? t("hub.noResultsHint") : t("hub.noGamesHint")}
        action={query ? <button type="button" className={actionClass("secondary")} onClick={onClear}>{t("hub.clearSearch")}</button> : undefined}
      />
    );
  }
  return (
    <div className="space-y-4">
      <p role="status" className="text-sm text-text-secondary">{query ? t("hub.totalFor", { count: formatCount(data.totalElements, locale), query }) : t("hub.total", { count: formatCount(data.totalElements, locale) })}</p>
      {summariesFailed ? (
        <p className="text-sm text-text-secondary">
          {t("hub.summaryUnavailable")}{" "}
          <button type="button" onClick={onRetrySummaries} className={`rounded-control underline underline-offset-2 ${focusRing}`}>{t("retry")}</button>
        </p>
      ) : null}
      <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {data.items.map((item) => (
          <li key={item.game.gameId}>
            <GameResult item={item} summary={summaries.get(item.game.gameId.toLowerCase())} loading={summariesLoading} />
          </li>
        ))}
      </ul>
      <Pagination page={data.page} size={data.size} totalElements={data.totalElements} label={t("hub.pagination")} onChange={onPage} />
    </div>
  );
}

function GameResult({ item, summary, loading }: { item: ReviewGameSearchItem; summary?: RatingSummary; loading: boolean }) {
  const t = useTranslations("Review");
  const locale = useLocale();
  const { game } = item;
  const average = summary ? formatAverage(summary.averageRating, locale) : null;
  return (
    <article className="flex h-full min-w-0 flex-col overflow-hidden rounded-panel border border-border bg-surface">
      <ReviewCover url={safeCoverUrl(game)} title={game.title} />
      <div className="flex flex-1 flex-col gap-3 p-4">
        <h3 className="font-display text-lg font-semibold [overflow-wrap:anywhere]">{game.title}</h3>
        <p className="text-sm text-text-secondary">
          {summary
            ? average
              ? t("hub.rating", { average, count: formatCount(summary.ratingCount, locale) })
              : t("hub.noRating")
            : loading
              ? t("loading")
              : t("hub.ratingUnknown")}
        </p>
        <Link
          href={{ pathname: "/reviews/games/[gameId]", params: { gameId: game.gameId } }}
          aria-label={t("hub.openFor", { title: game.title })}
          className={`${actionClass("primary")} mt-auto w-full`}
        >
          {t("hub.open")}
        </Link>
      </div>
    </article>
  );
}
