"use client";

import { useId } from "react";
import { useTranslations } from "next-intl";
import { Select, type SelectOption } from "@/shared/components/ui/select";
import { useCatalogTerms } from "../../hooks/use-enrichment";
import { useLabels } from "../../hooks/use-labels";
import type { CatalogTerm } from "../../types/recommendation";

export type GameFilterValue = { genre: string; platform: string; size: number };

/** Katalog terimleri → seçenek listesi. Yalnız oyunu olan terimler, yerelleştirilmiş ada göre sıralı; ilk seçenek "Tümü". */
export function useTermOptions(kind: "GENRE" | "PLATFORM", allLabel: string): { options: SelectOption[]; failed: boolean; loading: boolean } {
  const terms = useCatalogTerms(kind);
  const labels = useLabels();
  const entries = (terms.data ?? []).filter((term: CatalogTerm) => term.gameCount > 0)
    .map((term) => ({ value: term.code, label: labels.term(terms.data, term.code) }))
    .sort((a, b) => a.label.localeCompare(b.label));
  return { options: [{ value: "", label: allLabel }, ...entries], failed: terms.failed, loading: terms.loading };
}

/**
 * Oyun önerisi filtreleri. Tür/platform seçenekleri Game Service kataloğundan gelir (backend aynı kodlarla eşleştirir);
 * katalog alınamazsa yalnız "Tümü" kalır ve bu durum açıkça yazılır.
 */
export function GameFilters({ value, onChange, sizes, disabled }: { value: GameFilterValue; onChange: (value: GameFilterValue) => void; sizes: readonly number[]; disabled?: boolean }) {
  const t = useTranslations("Recommendation.filters");
  const id = useId();
  const genres = useTermOptions("GENRE", t("allGenres"));
  const platforms = useTermOptions("PLATFORM", t("allPlatforms"));
  const sizeOptions = sizes.map((size) => ({ value: String(size), label: t("sizeOption", { count: size }) }));
  return <div className="grid gap-4 sm:grid-cols-3">
    <div className="min-w-0">
      <label htmlFor={`${id}-genre`} className="mb-1.5 block text-sm font-medium">{t("genre")}</label>
      <Select id={`${id}-genre`} options={genres.options} value={value.genre} disabled={disabled} onChange={(genre) => onChange({ ...value, genre })} className="w-full" />
    </div>
    <div className="min-w-0">
      <label htmlFor={`${id}-platform`} className="mb-1.5 block text-sm font-medium">{t("platform")}</label>
      <Select id={`${id}-platform`} options={platforms.options} value={value.platform} disabled={disabled} onChange={(platform) => onChange({ ...value, platform })} className="w-full" />
    </div>
    <div className="min-w-0">
      <label htmlFor={`${id}-size`} className="mb-1.5 block text-sm font-medium">{t("size")}</label>
      <Select id={`${id}-size`} options={sizeOptions} value={String(value.size)} disabled={disabled} onChange={(size) => onChange({ ...value, size: Number(size) })} className="w-full" />
    </div>
    {genres.failed || platforms.failed ? <p className="text-xs text-text-secondary sm:col-span-3">{t("termsUnavailable")}</p> : null}
  </div>;
}
