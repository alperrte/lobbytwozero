"use client";

import { useId, useMemo, useState, type FormEvent } from "react";
import { useLocale, useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { activeFilters, filterChipKeys, validateFilters } from "../services/discovery-query";
import { controlClass } from "./game-status";
import { GameCombobox } from "./game-combobox";
import { GameDatePicker } from "./game-date-picker";
import { termLabel } from "../services/format";
import { marketForCurrency, priceMarkets } from "../services/price-markets";
import { languageOptions } from "../services/language-options";
import { useGameResource } from "../hooks/use-game-resource";
import type { TermList } from "../types/game";
import { Badge, checkboxClass, GameSelect, primaryButtonClass } from "./game-ui";

const textKeys = ["genre", "platform", "language"] as const;
const resetKeys = [...filterChipKeys, "country", "currency", "page"];

type Draft = Record<string, string>;
function draftFrom(params: URLSearchParams): Draft {
  return Object.fromEntries([...filterChipKeys, "currency"].map((key) => [key, params.get(key) ?? ""]));
}

/**
 * Tür/platform alanı: seçenekler yalnız public katalogdaki gerçek terimlerden gelir (GET /api/v1/game/terms). Uç boş, yüklenmemiş
 * veya hatalıysa seçenek uydurulmaz; alan serbest metin girişi olarak çalışır (filtre kodu elle yazılabilir).
 */
function TermCombobox({ id, termKind, value, onValueChange, placeholder }: { id: string; termKind: "GENRE" | "PLATFORM"; value: string; onValueChange: (value: string) => void; placeholder: string }) {
  const locale = useLocale();
  const result = useGameResource<TermList>({ kind: "terms", termKind }, { refreshOnFocus: false });
  const options = useMemo(() => (result.data?.items ?? []).map((item) => ({ value: item.code, label: termLabel(item, locale) })), [result.data, locale]);
  return <GameCombobox id={id} value={value} onValueChange={onValueChange} options={options} placeholder={placeholder} />;
}

/** Filtre paneli: değerler yerel taslakta tutulur, "Uygula" ile tek seferde URL'ye yazılır (tuş başına istek yok). */
export function GameFilters({ params, onApply }: { params: URLSearchParams; onApply: (changes: Record<string, string>) => void }) {
  const t = useTranslations("Game");
  const locale = useLocale();
  const languages = useMemo(() => languageOptions(locale), [locale]);
  const id = useId();
  const count = activeFilters(params).length;
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState<Draft>(() => draftFrom(params));
  const [source, setSource] = useState(params.toString());
  if (source !== params.toString()) { setSource(params.toString()); setDraft(draftFrom(params)); }
  const errors = validateFilters(new URLSearchParams(draft));
  const set = (key: string, value: string) => setDraft((previous) => ({ ...previous, [key]: value }));
  function submit(event: FormEvent) {
    event.preventDefault();
    if (errors.length) return;
    // Ülke ve para birimi birlikte seçilir (USD→US, EUR→DE, GBP→GB); desteklenmeyen eski değer (TRY) USD'ye düşer.
    onApply({ ...Object.fromEntries(Object.entries(draft).map(([key, value]) => [key, value.trim()])), country: marketForCurrency(draft.currency).country, currency: marketForCurrency(draft.currency).currency, page: "" });
  }
  const panel = `${id}-panel`;
  return <div>
    <button type="button" aria-expanded={open} aria-controls={panel} onClick={() => setOpen(!open)} className={`${controlClass} inline-flex items-center gap-2`}>
      <Icon name="settings" width={18} height={18} />{t("filters")}{count > 0 && <Badge tone="brand">{count}</Badge>}
    </button>
    {open && <form id={panel} onSubmit={submit} aria-label={t("filters")} className="mt-4 rounded-panel border border-border bg-surface p-5">
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {textKeys.map((key) => key === "language"
          ? <div key={key} className="flex min-w-0 flex-col gap-2 text-sm"><label htmlFor={`${id}-language`}>{t(`fields.${key}`)}</label>
            <GameCombobox id={`${id}-language`} value={draft.language} onValueChange={(next) => set("language", next)} options={languages} placeholder={t("languagePlaceholder")} /></div>
          : <div key={key} className="flex min-w-0 flex-col gap-2 text-sm"><label htmlFor={`${id}-${key}`}>{t(`fields.${key}`)}</label>
            <TermCombobox id={`${id}-${key}`} termKind={key === "genre" ? "GENRE" : "PLATFORM"} value={draft[key]} onValueChange={(next) => set(key, next)} placeholder={t(key === "genre" ? "genrePlaceholder" : "platformPlaceholder")} /></div>)}
        <label className="flex flex-col gap-2 text-sm">{t("fields.earlyAccess")}
          <GameSelect className={controlClass} value={draft.earlyAccess} onChange={(event) => set("earlyAccess", event.target.value)}><option value="">{t("all")}</option><option value="true">{t("yes")}</option><option value="false">{t("no")}</option></GameSelect></label>
        <div className="flex flex-col gap-2 text-sm"><label htmlFor={`${id}-releasedAfter`}>{t("fields.releasedAfter")}</label><GameDatePicker id={`${id}-releasedAfter`} value={draft.releasedAfter} onValueChange={(next) => set("releasedAfter", next)} min="0001-01-01" max="9999-12-31" invalid={errors.includes("dateRange")} describedBy={errors.includes("dateRange") ? `${id}-date` : undefined} /></div>
        <div className="flex flex-col gap-2 text-sm"><label htmlFor={`${id}-releasedBefore`}>{t("fields.releasedBefore")}</label><GameDatePicker id={`${id}-releasedBefore`} value={draft.releasedBefore} onValueChange={(next) => set("releasedBefore", next)} min="0001-01-01" max="9999-12-31" invalid={errors.includes("dateRange")} describedBy={errors.includes("dateRange") ? `${id}-date` : undefined} /></div>
        <label className="flex flex-col gap-2 text-sm">{t("fields.minRating")}<input type="number" inputMode="decimal" min={1} max={10} step="0.01" value={draft.minRating} onChange={(event) => set("minRating", event.target.value)} className={controlClass} aria-invalid={errors.includes("minRating") || undefined} aria-describedby={errors.includes("minRating") ? `${id}-rating` : undefined} /></label>
        <label className="flex flex-col gap-2 text-sm">{t("fields.currency")}<GameSelect className={controlClass} value={marketForCurrency(draft.currency).currency} onChange={(event) => set("currency", event.target.value)}>{priceMarkets.map((market) => <option key={market.currency} value={market.currency}>{market.currency}</option>)}</GameSelect></label>
        {["popularOnly", "discountedOnly"].map((key) => <label key={key} className="flex min-h-11 items-center gap-3 self-end text-sm"><input type="checkbox" className={checkboxClass} checked={draft[key] === "true"} onChange={(event) => set(key, event.target.checked ? "true" : "")} />{t(`fields.${key}`)}</label>)}
      </div>
      {errors.includes("dateRange") && <p id={`${id}-date`} role="alert" className="mt-4 text-sm text-brand-text">{t("filterErrors.dateRange")}</p>}
      {errors.includes("minRating") && <p id={`${id}-rating`} role="alert" className="mt-4 text-sm text-brand-text">{t("filterErrors.minRating")}</p>}
      <p className="mt-4 text-sm text-text-secondary">{t("filterHelp")}</p>
      <div className="mt-4 flex flex-wrap gap-3">
        <button type="submit" className={primaryButtonClass} disabled={errors.length > 0}>{t("applyFilters")}</button>
        <button type="button" className={controlClass} onClick={() => onApply(Object.fromEntries(resetKeys.map((key) => [key, ""])))}>{t("clearFilters")}</button>
      </div>
    </form>}
  </div>;
}

/** Aktif filtreler: her biri tek tek kaldırılabilir; hepsi birlikte temizlenebilir. */
export function GameActiveFilters({ params, onChange }: { params: URLSearchParams; onChange: (changes: Record<string, string>) => void }) {
  const t = useTranslations("Game");
  const chips = [
    ...activeFilters(params).map(({ key, value }) => ({ key, text: key === "popularOnly" || key === "discountedOnly" ? t(`fields.${key}`) : `${t(`fields.${key}`)}: ${key === "earlyAccess" ? t(value === "true" ? "yes" : "no") : value}` })),
    ...(params.get("currency") && marketForCurrency(params.get("currency")).currency !== "USD" ? [{ key: "currency", text: `${t("fields.currency")}: ${marketForCurrency(params.get("currency")).currency}` }] : [])
  ];
  if (!chips.length) return null;
  return <div className="flex flex-wrap items-center gap-2" role="group" aria-label={t("activeFilters")}>
    {chips.map((chip) => <button key={chip.key} type="button" onClick={() => onChange({ [chip.key]: "", page: "" })} aria-label={t("removeFilter", { filter: chip.text })}
      className="inline-flex min-h-9 items-center gap-2 rounded-full border border-control-border bg-surface px-3 text-sm hover:border-primary hover:text-brand-text">
      <span className="[overflow-wrap:anywhere]">{chip.text}</span><Icon name="close" width={14} height={14} /></button>)}
    <button type="button" className="min-h-9 px-2 text-sm text-brand-text underline-offset-4 hover:underline" onClick={() => onChange(Object.fromEntries(resetKeys.map((key) => [key, ""])))}>{t("clearFilters")}</button>
  </div>;
}
