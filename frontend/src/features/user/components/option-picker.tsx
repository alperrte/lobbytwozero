"use client";

import { useId, useMemo, useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { cn } from "@/shared/utils/cn";

export type PickerOption = { code: string; label: string; group?: string };
export type PickerGroup = { id: string; label: string };

/**
 * Çoklu seçim: seçilenler üstte kaldırılabilir çipler, altında aranabilir seçenek düğmeleri (`aria-pressed`). En çok `max`
 * seçilir; sınıra gelince seçilmemişler devre dışı kalır. Katalogda olmayan (eski, serbest yazılmış) kodlar seçili çip olarak
 * görünür ve kaldırılabilir; kayıt sırasında yine geçerli kod biçimindedirler.
 */
export function OptionPicker({
  label,
  hint,
  options,
  groups,
  value,
  onChange,
  max
}: {
  label: string;
  hint: string;
  options: readonly PickerOption[];
  groups?: readonly PickerGroup[];
  value: string[];
  onChange: (value: string[]) => void;
  max: number;
}) {
  const t = useTranslations("Profile.picker");
  const locale = useLocale();
  const searchId = useId();
  const [query, setQuery] = useState("");
  const full = value.length >= max;

  const byCode = useMemo(() => new Map(options.map((option) => [option.code, option])), [options]);
  const visible = useMemo(() => {
    const needle = query.trim().toLocaleLowerCase(locale);
    const matches =
      needle === ""
        ? [...options]
        : options.filter(
            (option) => option.label.toLocaleLowerCase(locale).includes(needle) || option.code.includes(needle)
          );
    const collator = new Intl.Collator(locale);
    return groups ? matches : matches.sort((a, b) => collator.compare(a.label, b.label));
  }, [options, groups, query, locale]);

  function toggle(code: string) {
    if (value.includes(code)) {
      onChange(value.filter((item) => item !== code));
    } else if (!full) {
      onChange([...value, code]);
    }
  }

  const sections: Array<{ id: string; label: string | null; items: PickerOption[] }> = groups
    ? groups
        .map((group) => ({ id: group.id, label: group.label, items: visible.filter((option) => option.group === group.id) }))
        .filter((section) => section.items.length > 0)
    : [{ id: "all", label: null, items: visible }];

  return (
    <fieldset className="flex flex-col gap-3">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <legend className="text-sm font-medium">{label}</legend>
        <span className={cn("text-xs", full ? "text-brand-text" : "text-text-secondary")} aria-live="polite">
          {t("count", { count: value.length, max })}
        </span>
      </div>
      <p className="text-xs text-text-secondary">{hint}</p>

      {value.length > 0 ? (
        <ul className="flex flex-wrap gap-2" aria-label={t("selected")}>
          {value.map((code) => {
            const option = byCode.get(code);
            return (
              <li key={code}>
                <button
                  type="button"
                  onClick={() => toggle(code)}
                  aria-label={t("remove", { name: option?.label ?? code })}
                  className="inline-flex min-h-8 items-center gap-1.5 rounded-full border border-primary bg-primary/15 ps-3 pe-2 text-sm text-brand-text"
                >
                  {option?.label ?? code}
                  <span aria-hidden="true" className="text-base leading-none">
                    ×
                  </span>
                </button>
              </li>
            );
          })}
        </ul>
      ) : null}

      <input
        id={searchId}
        type="search"
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        aria-label={t("search")}
        placeholder={t("search")}
        autoComplete="off"
        className="min-h-10 w-full max-w-sm rounded-control border border-control-border bg-surface px-3 text-text-primary placeholder:text-text-secondary"
      />

      <div className="flex max-h-72 flex-col gap-4 overflow-y-auto rounded-control border border-border bg-surface-elevated p-3">
        {sections.length === 0 ? <p className="text-sm text-text-secondary">{t("empty")}</p> : null}
        {sections.map((section) => (
          <div key={section.id} className="flex flex-col gap-2">
            {section.label ? <p className="text-xs font-semibold uppercase tracking-wide text-text-secondary">{section.label}</p> : null}
            <ul className="flex flex-wrap gap-2">
              {section.items.map((option) => {
                const on = value.includes(option.code);
                return (
                  <li key={option.code}>
                    <button
                      type="button"
                      aria-pressed={on}
                      disabled={!on && full}
                      onClick={() => toggle(option.code)}
                      className={cn(
                        "min-h-9 rounded-full border px-3 text-sm transition-colors duration-150 disabled:cursor-not-allowed disabled:opacity-50",
                        on
                          ? "border-primary bg-primary/15 text-brand-text"
                          : "border-border bg-surface text-text-primary hover:border-control-border"
                      )}
                    >
                      {option.label}
                    </button>
                  </li>
                );
              })}
            </ul>
          </div>
        ))}
      </div>
    </fieldset>
  );
}
