"use client";

import { useId, useState, type FormEvent } from "react";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { cn } from "@/shared/utils/cn";
import { isInvalidRange } from "../services/post-query";
import { emptyFilters, postCategories, postSorts, type PostFilters } from "../types/community.types";
import { buttonClass, FieldError, inputClass, panel } from "./community-ui";
import { GamePicker } from "./game-picker";

export type FilterOptions = { game?: boolean; category?: boolean; dates?: boolean };

/**
 * Akış arama/filtre/sıralama çubuğu (yalnız API'nin desteklediği parametreler: q, gameId, category, from/to, sort).
 * Metin araması gönderimle uygulanır (her tuşta istek atılmaz); diğer seçimler hemen uygulanır.
 */
export function PostFilterBar({
  filters,
  onChange,
  options = { game: true, category: true, dates: true }
}: {
  filters: PostFilters;
  onChange: (next: PostFilters) => void;
  options?: FilterOptions;
}) {
  const t = useTranslations("Community");
  const id = useId();
  const hasExtra = Boolean(options.game || options.category || options.dates);
  const extraActive = Boolean(filters.gameId || filters.category || filters.fromDate || filters.toDate);
  // Ek filtre paneli etkin filtrelerle açık başlar; sonrasında serbestçe açılıp kapanır.
  const [expanded, setExpanded] = useState(extraActive);
  const showExtra = hasExtra && expanded;
  const invalidRange = isInvalidRange(filters);
  const active = JSON.stringify({ ...filters, sort: "NEWEST" }) !== JSON.stringify(emptyFilters);

  return (
    <section aria-label={t("filters.label")} className={cn(panel, "mb-5 px-4 py-3")}>
      {/* Tek satır: sıralama solda; kompakt arama, Ara ve "Daha fazla filtre" sağda. Dar ekranda satırlar kendiliğinden sarar. */}
      <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-3">
        <div role="group" aria-label={t("filters.sortLabel")} className="flex flex-wrap gap-1">
          {postSorts.map((sort) => (
            <button
              key={sort}
              type="button"
              aria-pressed={filters.sort === sort}
              onClick={() => onChange({ ...filters, sort })}
              className={cn(
                "h-11 rounded-control px-4 text-sm font-semibold transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus",
                filters.sort === sort ? "bg-primary text-primary-foreground" : "text-text-secondary hover:bg-surface-elevated hover:text-text-primary"
              )}
            >
              {t(`filters.sort.${sort}`)}
            </button>
          ))}
        </div>

        <div className="flex w-full flex-wrap items-center gap-3 sm:ms-auto sm:w-auto">
          <SearchForm key={filters.q} id={id} initial={filters.q} onSubmit={(q) => onChange({ ...filters, q })} />
          {hasExtra ? (
            <button
              type="button"
              className={buttonClass}
              aria-expanded={showExtra}
              aria-controls={`${id}-extra`}
              onClick={() => setExpanded((value) => !value)}
            >
              {t("filters.more")}
            </button>
          ) : null}
        </div>
      </div>
      {showExtra ? (
        <div id={`${id}-extra`} className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {options.game ? (
            <GamePicker id={`${id}-game`} label={t("filters.game")} value={filters.gameId} onChange={(gameId) => onChange({ ...filters, gameId })} />
          ) : null}
          {options.category ? (
            <div>
              <label htmlFor={`${id}-category`} className="mb-1.5 block text-sm font-medium text-text-primary">
                {t("filters.category")}
              </label>
              <select
                id={`${id}-category`}
                value={filters.category}
                onChange={(event) => onChange({ ...filters, category: event.target.value as PostFilters["category"] })}
                className={inputClass}
              >
                <option value="">{t("filters.allCategories")}</option>
                {postCategories.map((category) => (
                  <option key={category} value={category}>
                    {t(`category.${category}`)}
                  </option>
                ))}
              </select>
            </div>
          ) : null}
          {options.dates ? (
            <>
              <div>
                <label htmlFor={`${id}-from`} className="mb-1.5 block text-sm font-medium text-text-primary">
                  {t("filters.from")}
                </label>
                <input id={`${id}-from`} type="date" value={filters.fromDate} max={filters.toDate || undefined} onChange={(event) => onChange({ ...filters, fromDate: event.target.value })} className={inputClass} />
              </div>
              <div>
                <label htmlFor={`${id}-to`} className="mb-1.5 block text-sm font-medium text-text-primary">
                  {t("filters.to")}
                </label>
                <input id={`${id}-to`} type="date" value={filters.toDate} min={filters.fromDate || undefined} onChange={(event) => onChange({ ...filters, toDate: event.target.value })} className={inputClass} />
              </div>
            </>
          ) : null}
        </div>
      ) : null}
      {invalidRange ? <FieldError id={`${id}-range`}>{t("filters.invalidRange")}</FieldError> : null}

      {showExtra && active ? (
        <div className="mt-3">
          <button
            type="button"
            className={buttonClass}
            onClick={() => onChange({ ...emptyFilters, sort: filters.sort })}
          >
            {t("filters.clear")}
          </button>
        </div>
      ) : null}
    </section>
  );
}

/** Arama gönderimle uygulanır (her tuşta istek atılmaz); en az 2 karakter sunucu kuralıdır. `key` ile dışarıdan sıfırlanır. */
function SearchForm({ id, initial, onSubmit }: { id: string; initial: string; onSubmit: (q: string) => void }) {
  const t = useTranslations("Community");
  const [draft, setDraft] = useState(initial);
  const short = draft.trim().length === 1;

  function submit(event: FormEvent) {
    event.preventDefault();
    onSubmit(draft.trim().length >= 2 ? draft.trim() : "");
  }

  return (
    <form onSubmit={submit} role="search" className="flex w-full flex-wrap items-center gap-x-3 gap-y-1 sm:w-auto">
      <label htmlFor={`${id}-q`} className="sr-only">
        {t("filters.search")}
      </label>
      <div className="relative min-w-0 flex-1 sm:w-64 sm:flex-none">
        <Icon name="search" className="pointer-events-none absolute start-3 top-1/2 size-4 -translate-y-1/2 text-text-secondary" />
        <input
          id={`${id}-q`}
          type="search"
          value={draft}
          maxLength={100}
          onChange={(event) => setDraft(event.target.value)}
          aria-describedby={short ? `${id}-q-hint` : undefined}
          placeholder={t("filters.searchPlaceholder")}
          className={cn(inputClass, "ps-9")}
        />
      </div>
      <button type="submit" className={cn(buttonClass, "shrink-0")}>
        {t("filters.apply")}
      </button>
      {short ? (
        <p id={`${id}-q-hint`} className="w-full text-sm text-text-secondary">
          {t("filters.searchMin")}
        </p>
      ) : null}
    </form>
  );
}
