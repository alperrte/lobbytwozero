"use client";

import { useId, useRef, useState, type KeyboardEvent } from "react";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { cn } from "@/shared/utils/cn";
import { controlClass } from "./game-status";

export type ComboOption = { value: string; label: string };

type Props = {
  id: string;
  value: string;
  onValueChange: (value: string) => void;
  /** Doğrulanmış gerçek seçenekler. Boşsa alan düz metin girişi gibi davranır (öneri uydurulmaz). */
  options: readonly ComboOption[];
  placeholder?: string;
  maxLength?: number;
  invalid?: boolean;
  describedBy?: string;
};

const fold = (text: string) => text.toLocaleLowerCase("tr").normalize("NFD").replace(/\p{Diacritic}/gu, "");

/**
 * Otomatik tamamlamalı açılır liste (ARIA combobox, liste kutusu deseni). Yazdıkça seçenekler etiket ve koda göre süzülür;
 * oklar, Enter, Esc ve Tab ile kullanılır. Serbest metin her zaman kabul edilir: etiketle tam eşleşen metin koda çevrilir,
 * diğer metin olduğu gibi gönderilir. Seçenek yoksa açılır liste ve ok düğmesi gösterilmez.
 */
export function GameCombobox({ id, value, onValueChange, options, placeholder, maxLength = 64, invalid, describedBy }: Props) {
  const t = useTranslations("Game");
  const listId = useId();
  const root = useRef<HTMLDivElement>(null);
  const [text, setText] = useState(() => options.find((option) => option.value === value)?.label ?? value);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const hasOptions = options.length > 0;

  // Dışarıdan gelen değer (ör. filtreleri temizle) metne yansır; yazılmakta olan metin korunur.
  const [lastValue, setLastValue] = useState(value);
  if (value !== lastValue) {
    setLastValue(value);
    const asValue = options.find((option) => fold(option.label) === fold(text))?.value ?? text;
    if (asValue !== value) setText(options.find((option) => option.value === value)?.label ?? value);
  }

  const query = fold(text.trim());
  const exact = options.some((option) => fold(option.label) === query || option.value === text.trim());
  const filtered = !query || exact ? options : options.filter((option) => fold(option.label).includes(query) || fold(option.value).includes(query));

  const activeIndex = Math.max(0, Math.min(active, filtered.length - 1));

  function choose(option: ComboOption) {
    setText(option.label);
    onValueChange(option.value);
    setOpen(false);
  }

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (!hasOptions) return;
    if (event.key === "ArrowDown") { event.preventDefault(); if (!open) setOpen(true); else setActive(Math.min(activeIndex + 1, filtered.length - 1)); }
    else if (event.key === "ArrowUp") { event.preventDefault(); setActive(Math.max(activeIndex - 1, 0)); }
    else if (event.key === "Enter" && open && filtered[activeIndex]) { event.preventDefault(); choose(filtered[activeIndex]); }
    else if (event.key === "Escape" && open) { event.preventDefault(); setOpen(false); }
  }

  return <div ref={root} className="relative block" onBlur={(event) => { if (!root.current?.contains(event.relatedTarget as Node | null)) setOpen(false); }}>
    <input id={id} type="text" role={hasOptions ? "combobox" : undefined} aria-autocomplete={hasOptions ? "list" : undefined} aria-expanded={hasOptions ? open : undefined}
      aria-controls={hasOptions && open ? listId : undefined} aria-activedescendant={hasOptions && open && filtered[activeIndex] ? `${listId}-${activeIndex}` : undefined}
      aria-invalid={invalid || undefined} aria-describedby={describedBy} autoComplete="off" maxLength={maxLength} placeholder={placeholder} value={text}
      onChange={(event) => {
        const next = event.target.value;
        setText(next);
        setActive(0);
        setOpen(hasOptions);
        onValueChange(options.find((option) => fold(option.label) === fold(next.trim()))?.value ?? next);
      }}
      onFocus={() => { if (hasOptions) { setActive(0); setOpen(true); } }}
      onKeyDown={onKeyDown}
      className={cn(controlClass, "w-full min-w-0 cursor-text hover:border-primary/60 focus-visible:border-primary", hasOptions && "pe-10")} />
    {hasOptions && <button type="button" tabIndex={-1} aria-label={t("comboboxShowOptions")} aria-expanded={open} aria-controls={open ? listId : undefined} onMouseDown={(event) => event.preventDefault()}
      onClick={() => setOpen(!open)} className="absolute end-1 top-1/2 inline-flex size-9 -translate-y-1/2 cursor-pointer items-center justify-center rounded-control text-text-secondary transition-colors hover:bg-surface-elevated hover:text-text-primary">
      <Icon name="chevron" width={16} height={16} className={cn("transition-transform", open && "rotate-180")} aria-hidden="true" />
    </button>}
    {hasOptions && open && <ul id={listId} role="listbox" aria-label={t("comboboxShowOptions")} className="absolute start-0 top-full z-30 mt-2 max-h-64 w-full overflow-auto rounded-panel border border-border bg-surface p-1 shadow-lg">
      {filtered.length === 0
        ? <li role="presentation" className="px-3 py-2 text-sm text-text-secondary">{t("comboboxNoOptions")}</li>
        : filtered.map((option, index) => <li key={option.value} id={`${listId}-${index}`} role="option" aria-selected={option.value === value}
            onMouseDown={(event) => event.preventDefault()} onClick={() => choose(option)} onMouseEnter={() => setActive(index)}
            className={cn("flex min-h-10 cursor-pointer items-center justify-between gap-3 rounded-control px-3 py-2 text-sm text-text-primary", index === activeIndex && "bg-surface-elevated", option.value === value && "font-semibold text-brand-text")}>
            <span className="min-w-0 [overflow-wrap:anywhere]">{option.label}</span>
            <span className="shrink-0 text-xs text-text-secondary">{option.value}</span>
          </li>)}
    </ul>}
  </div>;
}
