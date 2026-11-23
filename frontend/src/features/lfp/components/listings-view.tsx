"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { Icon } from "@/shared/components/ui/icons";
import { SearchField } from "@/shared/components/ui/search-field";
import { Link, useRouter } from "@/shared/lib/i18n/navigation";
import { cn } from "@/shared/utils/cn";
import { useContactOwner, useListingSearch, useListingVisuals } from "../hooks/use-lfp-data";
import { activeFilterCount, filtersFromParams, filtersToQuery } from "../schemas/listing-form";
import { lfpErrorKey } from "../services/lfp-errors";
import type { Listing, ListingFilters } from "../types/lfp.types";
import { ListingCard } from "./listing-card";
import { ListingFiltersPanel } from "./listing-filters";
import { LfpSessionGate, LfpShell } from "./listing-parts";

export function ListingsView() {
  return <LfpSessionGate>{(meId) => <ListingsScreen meId={meId} />}</LfpSessionGate>;
}

/**
 * Oyun Arkadaşı İlanları (11.01; LFP-10, LFP-11, LFP-16). Yalnız açık ilanlar listelenir; süzgeçler ve arama adres çubuğundadır
 * (paylaşılabilir, geri/ileri çalışır). Kendi ilanında "Mesaj Gönder" yerine "Düzenle" vardır.
 */
function ListingsScreen({ meId }: Readonly<{ meId: string }>) {
  const t = useTranslations("Lfp");
  const params = useSearchParams();
  const router = useRouter();
  const paramKey = params.toString();
  const filters = useMemo(() => filtersFromParams(new URLSearchParams(paramKey)), [paramKey]);
  const [gameTitle, setGameTitle] = useState<string | null>(null);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const search = useListingSearch(filters);
  const visuals = useListingVisuals(search.items);
  const contact = useContactOwner();

  const apply = useCallback(
    (next: ListingFilters, title?: string | null) => {
      if (title !== undefined) setGameTitle(title);
      router.replace({ pathname: "/find-team", query: filtersToQuery(next) }, { scroll: false });
    },
    [router]
  );

  const [draft, setDraft] = useState(filters.q ?? "");
  const [draftFor, setDraftFor] = useState(filters.q ?? "");
  if (draftFor !== (filters.q ?? "")) {
    setDraftFor(filters.q ?? "");
    setDraft(filters.q ?? "");
  }
  useEffect(() => {
    if (draft.trim() === (filters.q ?? "")) return;
    const timer = setTimeout(() => apply({ ...filters, q: draft.trim() || undefined }), 400);
    return () => clearTimeout(timer);
  }, [draft, filters, apply]);

  const count = activeFilterCount(filters);
  const shownGameTitle = filters.gameId ? gameTitle ?? search.items.find((item) => item.gameId === filters.gameId)?.gameTitle ?? null : null;

  const onContact = (listing: Listing) => {
    void contact.contact(listing.id);
  };

  return (
    <LfpShell section="active" title={t("active.title")} subtitle={t("active.subtitle")}>
      <div className="grid items-start gap-5 lg:grid-cols-[17rem_minmax(0,1fr)] xl:grid-cols-[19rem_minmax(0,1fr)]">
        <aside aria-labelledby="lfp-filters-title" className="rounded-panel border border-border bg-surface p-4 lg:sticky lg:top-header-offset">
          <div className="flex items-center justify-between gap-3">
            <h2 id="lfp-filters-title" className="flex items-center gap-2 font-display text-lg font-semibold">
              <Icon name="filter" className="size-5 text-brand-text" />
              {t("filters.title")}
              {count > 0 ? <span className="rounded-full bg-primary px-2 text-xs font-bold text-primary-foreground">{count}</span> : null}
            </h2>
            <div className="flex items-center gap-1">
              {count > 0 ? (
                <button type="button" onClick={() => apply({ q: filters.q }, null)} className="rounded-control px-2 py-1 text-sm font-semibold text-brand-text hover:bg-surface-elevated focus-visible:outline-2 focus-visible:outline-focus">
                  {t("filters.clear")}
                </button>
              ) : null}
              <button
                type="button"
                onClick={() => setFiltersOpen((open) => !open)}
                aria-expanded={filtersOpen}
                aria-controls="lfp-filters-body"
                className={buttonClass("ghost", "sm", "lg:hidden")}
              >
                {filtersOpen ? t("filters.hide") : t("filters.show")}
              </button>
            </div>
          </div>
          <div id="lfp-filters-body" className={cn("mt-4", !filtersOpen && "max-lg:hidden")}>
            <ListingFiltersPanel filters={filters} gameTitle={shownGameTitle} onChange={apply} />
          </div>
        </aside>

        <section aria-labelledby="lfp-results-title" className="min-w-0 space-y-4">
          <div className="flex flex-wrap items-center gap-3">
            <h2 id="lfp-results-title" className="me-auto font-display text-lg font-semibold">
              {search.loading ? t("active.loadingCount") : t("active.count", { count: search.items.length, more: search.hasMore ? "more" : "exact" })}
            </h2>
            <SearchField
              className="flex-1 basis-64 sm:max-w-sm"
              label={t("active.search")}
              placeholder={t("active.searchPlaceholder")}
              maxLength={100}
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
            />
          </div>

          {search.loading ? (
            <CardSkeleton label={t("states.loading")} />
          ) : search.error ? (
            <div role="alert" className="rounded-panel border border-primary/50 bg-surface px-6 py-10 text-center">
              <Icon name="info" className="mx-auto size-8 text-brand-text" />
              <p className="mt-3 text-sm text-text-secondary">{t(`errors.${lfpErrorKey(search.error)}`)}</p>
              <button type="button" onClick={search.reload} className={buttonClass("secondary", "md", "mt-5")}>
                <Icon name="refresh" className="size-4" />
                {t("states.retry")}
              </button>
            </div>
          ) : search.items.length === 0 && !search.hasMore ? (
            <div className="flex flex-col items-center rounded-panel border border-dashed border-border bg-surface px-6 py-12 text-center">
              <span className="inline-flex size-14 items-center justify-center rounded-full bg-surface-elevated text-text-secondary">
                <Icon name="usersRound" className="size-7" />
              </span>
              <h3 className="mt-4 font-display text-lg font-semibold">{count > 0 || filters.q ? t("active.noMatchTitle") : t("active.emptyTitle")}</h3>
              <p className="mt-1.5 max-w-sm text-sm text-text-secondary">{count > 0 || filters.q ? t("active.noMatchBody") : t("active.emptyBody")}</p>
              <Link href="/find-team/new" className={buttonClass("primary", "md", "mt-5")}>
                <Icon name="plus" className="size-4" />
                {t("actions.create")}
              </Link>
            </div>
          ) : (
            <ul className="grid gap-4 md:grid-cols-2 2xl:grid-cols-3">
              {search.items.map((listing) => (
                <li key={listing.id} className="flex min-w-0">
                  <ListingCard
                    listing={listing}
                    owner={listing.ownerId === meId ? { ...visuals.owner(meId), name: t("form.you"), handle: null } : visuals.owner(listing.ownerId)}
                    game={visuals.game(listing)}
                    mine={listing.ownerId === meId}
                    contacting={contact.pending === listing.id}
                    contactError={contact.error?.listingId === listing.id ? t(`errors.${contact.error.key}`) : null}
                    onContact={onContact}
                  />
                </li>
              ))}
            </ul>
          )}

          {!search.loading && !search.error && search.hasMore ? (
            <div className="flex flex-col items-center gap-2">
              {search.moreError ? <p role="alert" className="text-sm text-brand-text">{t(`errors.${lfpErrorKey(search.moreError)}`)}</p> : null}
              <button type="button" onClick={search.loadMore} disabled={search.loadingMore} aria-busy={search.loadingMore || undefined} className={buttonClass("secondary")}>
                {search.loadingMore ? t("states.loading") : t("active.loadMore")}
              </button>
            </div>
          ) : null}
        </section>
      </div>
    </LfpShell>
  );
}

function CardSkeleton({ label }: Readonly<{ label: string }>) {
  return (
    <div role="status" aria-live="polite">
      <span className="sr-only">{label}</span>
      <div aria-hidden="true" className="grid gap-4 md:grid-cols-2 2xl:grid-cols-3">
        {Array.from({ length: 4 }, (_, index) => (
          <div key={index} className="h-80 animate-pulse rounded-panel border border-border bg-surface motion-reduce:animate-none" />
        ))}
      </div>
    </div>
  );
}
