"use client";

import { useEffect, useId, useRef, useState, type ChangeEvent, type FormEvent } from "react";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { cn } from "@/shared/utils/cn";
import { isInvalidRange } from "../services/post-query";
import { emptyFilters, postCategories, type PostFilters } from "../types/community.types";
import { buttonClass, FieldError, inputClass, panel } from "./community-ui";
import { GamePicker } from "./game-picker";
import { SortCriteriaMenu } from "./sort-criteria-menu";

export type FilterOptions = { game?: boolean; category?: boolean; dates?: boolean };

/**
 * Akış arama/filtre/sıralama çubuğu (yalnız API'nin desteklediği parametreler: q, gameId, category, from/to, sort).
 * Arama yazarken kısa bir gecikmeyle (debounce) ya da Enter ile uygulanır; alan temizlenince hemen sıfırlanır. Sıralama "Filtrele"
 * çoklu seçim menüsündedir (bkz. `SortCriteriaMenu`); YALNIZ sunucunun sıralayabildiği ölçütler seçilebilir (yüklenmiş kayıtlar
 * istemcide sıralanmaz); sıralama ya da arama değişince liste ve sayfalama baştan yüklenir.
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
      {/* Tek satır: arama solda; sağ grupta "Filtrele" açılır listesi (sıralama) ve hemen sağında "Daha fazla filtre". Dar ekranda
          arama tam genişlikte ilk satırda kalır, sağ grup altına sarar (yatay taşma yok). */}
      <div className="flex flex-wrap items-center gap-x-3 gap-y-3">
        <SearchForm id={id} value={filters.q} onApply={(q) => onChange({ ...filters, q })} />
        <div className="flex w-full flex-wrap items-center gap-3 sm:ms-auto sm:w-auto sm:flex-nowrap">
          <SortCriteriaMenu value={filters.sort} onApply={(sort) => onChange({ ...filters, sort })} />
          {hasExtra ? (
            <button
              type="button"
              className={cn(buttonClass, "shrink-0")}
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

const SEARCH_DEBOUNCE_MS = 400;
/** Sunucu kuralı: arama en az 2 karakterdir; kısa metin aramayı sıfırlar. */
const effectiveQuery = (draft: string) => (draft.trim().length >= 2 ? draft.trim() : "");

/**
 * Arama alanı: yazarken `SEARCH_DEBOUNCE_MS` sonra uygulanır (her tuşta istek atılmaz), Enter hemen uygular, alan boşalınca arama
 * hemen sıfırlanır. En az 2 karakter sunucu kuralıdır. Dışarıdan değişen değer (filtreleri temizleme) alana yansır.
 */
function SearchForm({ id, value, onApply }: { id: string; value: string; onApply: (q: string) => void }) {
  const t = useTranslations("Community");
  const [draft, setDraft] = useState(value);
  const [synced, setSynced] = useState(value);
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const short = draft.trim().length === 1;

  // Dışarıdan gelen değer, yazılanın etkin karşılığından farklıysa (ör. filtreler temizlendi) alan güncellenir.
  if (value !== synced) {
    setSynced(value);
    if (effectiveQuery(draft) !== value) setDraft(value);
  }

  useEffect(() => () => clearTimeout(timer.current), []);

  function apply(next: string) {
    clearTimeout(timer.current);
    const q = effectiveQuery(next);
    if (q !== value) onApply(q);
  }

  function onChange(event: ChangeEvent<HTMLInputElement>) {
    const next = event.target.value;
    setDraft(next);
    clearTimeout(timer.current);
    if (next === "") apply("");
    else timer.current = setTimeout(() => apply(next), SEARCH_DEBOUNCE_MS);
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    apply(draft);
  }

  return (
    <form onSubmit={submit} role="search" className="w-full sm:w-72">
      <label htmlFor={`${id}-q`} className="sr-only">
        {t("filters.search")}
      </label>
      <div className="relative">
        <Icon name="search" className="pointer-events-none absolute start-3 top-1/2 size-4 -translate-y-1/2 text-text-secondary" />
        <input
          id={`${id}-q`}
          type="search"
          value={draft}
          maxLength={100}
          onChange={onChange}
          aria-describedby={short ? `${id}-q-hint` : undefined}
          placeholder={t("filters.searchPlaceholder")}
          className={cn(inputClass, "ps-9")}
        />
      </div>
      {short ? (
        <p id={`${id}-q-hint`} className="mt-1 text-sm text-text-secondary">
          {t("filters.searchMin")}
        </p>
      ) : null}
    </form>
  );
}
