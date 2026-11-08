"use client";

import { useId, useMemo, useRef, useState, type KeyboardEvent } from "react";
import { useLocale, useTranslations } from "next-intl";
import { usePopover } from "@/shared/hooks/use-popover";
import { cn } from "@/shared/utils/cn";
import { countryName, countryOptions } from "../catalog/countries";
import { CountryFlag } from "./country-flag";

const triggerClass =
  "flex min-h-11 w-full items-center gap-3 rounded-control border border-control-border bg-surface px-3 text-start text-text-primary";

/**
 * Ülke seçici: bayrak + ad + ISO kodu, arama kutulu liste (WAI-ARIA combobox/listbox). Değer ISO alpha-2 kodudur
 * (boş: seçilmedi). Klavye: ok tuşları gezinir, Enter seçer, Escape kapatır. Seçimi kaldırmak için "Seçimi temizle" satırı vardır.
 */
export function CountrySelect({
  id,
  value,
  onChange,
  describedBy
}: {
  id: string;
  value: string;
  onChange: (code: string) => void;
  describedBy?: string;
}) {
  const t = useTranslations("Profile.countryPicker");
  const locale = useLocale();
  const { open, setOpen, close, rootRef, triggerRef } = usePopover();
  const searchRef = useRef<HTMLInputElement>(null);
  const listId = useId();
  const [query, setQuery] = useState("");
  const [active, setActive] = useState(0);

  const all = useMemo(() => countryOptions(locale), [locale]);
  const filtered = useMemo(() => {
    const needle = query.trim().toLocaleLowerCase(locale);
    if (needle === "") return all;
    return all.filter(
      (option) => option.name.toLocaleLowerCase(locale).includes(needle) || option.code.toLowerCase() === needle
    );
  }, [all, query, locale]);

  // İlk satır "Seçimi temizle"dir (yalnız bir ülke seçiliyken); sonrası ülkelerdir.
  const hasClear = value !== "" && query.trim() === "";
  const rows = hasClear ? [{ code: "", name: t("clear") }, ...filtered] : filtered;
  const activeIndex = Math.min(active, Math.max(rows.length - 1, 0));

  function openList() {
    setQuery("");
    const index = all.findIndex((option) => option.code === value);
    setActive(index >= 0 ? index + (value !== "" ? 1 : 0) : 0);
    setOpen(true);
    requestAnimationFrame(() => searchRef.current?.focus());
  }

  function choose(code: string) {
    onChange(code);
    close(true);
  }

  function onSearchKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setActive(Math.min(activeIndex + 1, rows.length - 1));
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      setActive(Math.max(activeIndex - 1, 0));
    } else if (event.key === "Home") {
      event.preventDefault();
      setActive(0);
    } else if (event.key === "End") {
      event.preventDefault();
      setActive(rows.length - 1);
    } else if (event.key === "Enter") {
      event.preventDefault();
      const row = rows[activeIndex];
      if (row) choose(row.code);
    }
  }

  function scrollActive(node: HTMLLIElement | null, isActive: boolean) {
    if (node && isActive) node.scrollIntoView({ block: "nearest" });
  }

  return (
    <div ref={rootRef} className="relative">
      <button
        ref={triggerRef}
        id={id}
        type="button"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-describedby={describedBy}
        onClick={() => (open ? close() : openList())}
        className={triggerClass}
      >
        {value !== "" ? (
          <>
            <CountryFlag code={value} className="h-4 w-6" />
            <span className="min-w-0 flex-1 truncate">{countryName(value, locale)}</span>
            <span className="text-sm text-text-secondary">{value}</span>
          </>
        ) : (
          <span className="flex-1 text-text-secondary">{t("placeholder")}</span>
        )}
        <svg aria-hidden="true" viewBox="0 0 12 12" className={cn("size-3 shrink-0 text-text-secondary transition-transform", open && "rotate-180")}>
          <path d="M2 4.5 6 8.5 10 4.5" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </button>

      {open ? (
        <div className="absolute inset-x-0 z-20 mt-1 rounded-panel border border-border bg-surface-elevated p-2 shadow-lg">
          <input
            ref={searchRef}
            type="text"
            role="combobox"
            aria-expanded="true"
            aria-controls={listId}
            aria-activedescendant={rows[activeIndex] ? `${listId}-${rows[activeIndex].code || "clear"}` : undefined}
            aria-label={t("search")}
            placeholder={t("search")}
            autoComplete="off"
            value={query}
            onChange={(event) => {
              setQuery(event.target.value);
              setActive(0);
            }}
            onKeyDown={onSearchKeyDown}
            className="min-h-10 w-full rounded-control border border-control-border bg-surface px-3 text-text-primary placeholder:text-text-secondary"
          />
          <ul id={listId} role="listbox" aria-label={t("list")} className="mt-2 max-h-64 overflow-y-auto">
            {rows.length === 0 ? (
              <li role="presentation" className="px-3 py-2 text-sm text-text-secondary">
                {t("empty")}
              </li>
            ) : (
              rows.map((row, index) => (
                <li
                  key={row.code || "clear"}
                  id={`${listId}-${row.code || "clear"}`}
                  role="option"
                  aria-selected={row.code === value && row.code !== ""}
                  ref={(node) => scrollActive(node, index === activeIndex)}
                  onPointerDown={(event) => event.preventDefault()}
                  onClick={() => choose(row.code)}
                  onPointerEnter={() => setActive(index)}
                  className={cn(
                    "flex cursor-pointer items-center gap-3 rounded-control px-3 py-2 text-sm",
                    index === activeIndex && "bg-surface",
                    row.code === "" && "text-text-secondary"
                  )}
                >
                  {row.code !== "" ? <CountryFlag code={row.code} className="h-4 w-6" /> : null}
                  <span className="min-w-0 flex-1 truncate">{row.name}</span>
                  {row.code !== "" ? <span className="text-xs text-text-secondary">{row.code}</span> : null}
                </li>
              ))
            )}
          </ul>
        </div>
      ) : null}
    </div>
  );
}
