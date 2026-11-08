"use client";

import { useEffect, useId, useMemo, useRef, useState, type KeyboardEvent } from "react";
import { useLocale, useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { cn } from "@/shared/utils/cn";
import { controlClass } from "./game-status";
import { GameSelect } from "./game-ui";

/**
 * Özel tarih seçici. Değer her zaman ISO (`yyyy-mm-dd`) tutulur; ekranda yerel biçimde (tr: gg.aa.yyyy) gösterilir.
 * Alan elle yazılabilir (ISO veya yerel biçim) ve takvimden seçilebilir. Takvim: ay/yıl seçimi, klavye gezintisi
 * (oklar, Home/End, PageUp/PageDown, Esc), min/max sınırı, "Bugün" ve "Temizle". Yerel `input[type=date]` kullanılmaz.
 */
export type GameDatePickerProps = {
  id: string;
  /** Verilirse form gönderiminde ISO değer bu adla gider (gizli alan). */
  name?: string;
  value?: string;
  defaultValue?: string;
  onValueChange?: (iso: string) => void;
  min?: string;
  max?: string;
  invalid?: boolean;
  describedBy?: string;
  className?: string;
};

const ISO = /^(\d{4})-(\d{2})-(\d{2})$/;

function toIso(date: Date): string {
  return `${String(date.getUTCFullYear()).padStart(4, "0")}-${String(date.getUTCMonth() + 1).padStart(2, "0")}-${String(date.getUTCDate()).padStart(2, "0")}`;
}

function fromIso(iso: string): Date | null {
  const match = ISO.exec(iso);
  if (!match) return null;
  const date = new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3])));
  return toIso(date) === iso ? date : null;
}

function addDays(iso: string, days: number): string {
  const date = fromIso(iso) ?? new Date();
  date.setUTCDate(date.getUTCDate() + days);
  return toIso(date);
}

function addMonths(iso: string, months: number): string {
  const date = fromIso(iso) ?? new Date();
  const day = date.getUTCDate();
  date.setUTCDate(1);
  date.setUTCMonth(date.getUTCMonth() + months);
  const last = new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth() + 1, 0)).getUTCDate();
  date.setUTCDate(Math.min(day, last));
  return toIso(date);
}

/** Yerel gün/ay/yıl sırası ve ayracı Intl'den okunur; biçim ve ayrıştırma aynı kaynağı kullanır. */
function localeFormat(locale: string) {
  const parts = new Intl.DateTimeFormat(locale, { day: "2-digit", month: "2-digit", year: "numeric", timeZone: "UTC" }).formatToParts(new Date(Date.UTC(2006, 10, 22)));
  const order = parts.filter((part) => part.type === "day" || part.type === "month" || part.type === "year").map((part) => part.type as "day" | "month" | "year");
  const separator = parts.find((part) => part.type === "literal")?.value.trim() || ".";
  return { order, separator };
}

function formatIso(iso: string, locale: string): string {
  const date = fromIso(iso);
  if (!date) return "";
  const { order, separator } = localeFormat(locale);
  const values = { day: String(date.getUTCDate()).padStart(2, "0"), month: String(date.getUTCMonth() + 1).padStart(2, "0"), year: String(date.getUTCFullYear()).padStart(4, "0") };
  return order.map((part) => values[part]).join(separator);
}

/** ISO veya yerel biçimde tam bir tarih ise ISO döner; aksi halde null. */
function parseText(text: string, locale: string): string | null {
  const value = text.trim();
  if (fromIso(value)) return value;
  const pieces = value.split(/[./-]/);
  if (pieces.length !== 3 || pieces.some((piece) => !/^\d+$/.test(piece))) return null;
  const { order } = localeFormat(locale);
  const numbers = Object.fromEntries(order.map((part, index) => [part, pieces[index]])) as Record<"day" | "month" | "year", string>;
  if (numbers.year.length !== 4) return null;
  const iso = `${numbers.year}-${numbers.month.padStart(2, "0")}-${numbers.day.padStart(2, "0")}`;
  return fromIso(iso) ? iso : null;
}

export function GameDatePicker({ id, name, value, defaultValue = "", onValueChange, min, max, invalid, describedBy, className }: GameDatePickerProps) {
  const t = useTranslations("Game");
  const locale = useLocale();
  const dialogId = useId();
  const controlled = value !== undefined;
  const [inner, setInner] = useState(defaultValue);
  const iso = controlled ? value : inner;
  const [text, setText] = useState(() => formatIso(iso, locale));
  const [textInvalid, setTextInvalid] = useState(false);
  const [open, setOpen] = useState(false);
  const [focused, setFocused] = useState("");
  const [view, setView] = useState<{ year: number; month: number }>(() => {
    const base = fromIso(iso) ?? new Date();
    return { year: base.getUTCFullYear(), month: base.getUTCMonth() };
  });
  const root = useRef<HTMLDivElement>(null);
  const input = useRef<HTMLInputElement>(null);
  const cells = useRef(new Map<string, HTMLButtonElement>());
  const today = toIso(new Date());
  const lower = min && fromIso(min) ? min : "";
  const upper = max && fromIso(max) ? max : "";
  const outside = (candidate: string) => (lower !== "" && candidate < lower) || (upper !== "" && candidate > upper);

  // Dışarıdan gelen değer (ör. filtreleri temizle) metne yansır; yazma sırasındaki kısmi metin korunur.
  const shown = formatIso(iso, locale);
  const [lastShown, setLastShown] = useState(shown);
  if (shown !== lastShown) {
    setLastShown(shown);
    if (parseText(text, locale) !== iso) { setText(shown); setTextInvalid(false); }
  }

  function commit(next: string) {
    if (!controlled) setInner(next);
    onValueChange?.(next);
  }

  function select(next: string) {
    if (outside(next)) return;
    commit(next);
    setText(formatIso(next, locale));
    setTextInvalid(false);
    setOpen(false);
    input.current?.focus();
  }

  function openCalendar() {
    const base = fromIso(iso) ? iso : outside(today) ? (lower || upper) : today;
    const date = fromIso(base) ?? new Date();
    setView({ year: date.getUTCFullYear(), month: date.getUTCMonth() });
    setFocused(base);
    setOpen(true);
  }

  useEffect(() => {
    if (open && focused) cells.current.get(focused)?.focus();
  }, [open, focused, view]);

  function move(next: string) {
    if (outside(next)) return;
    const date = fromIso(next);
    if (!date) return;
    setView({ year: date.getUTCFullYear(), month: date.getUTCMonth() });
    setFocused(next);
  }

  function onGridKey(event: KeyboardEvent<HTMLDivElement>) {
    const key = event.key;
    const current = focused || today;
    const jumps: Record<string, string> = {
      ArrowLeft: addDays(current, -1), ArrowRight: addDays(current, 1), ArrowUp: addDays(current, -7), ArrowDown: addDays(current, 7),
      Home: addDays(current, -((((fromIso(current)?.getUTCDay() ?? 0) + 6) % 7))), End: addDays(current, 6 - (((fromIso(current)?.getUTCDay() ?? 0) + 6) % 7)),
      PageUp: addMonths(current, event.shiftKey ? -12 : -1), PageDown: addMonths(current, event.shiftKey ? 12 : 1)
    };
    if (key === "Escape") { event.preventDefault(); setOpen(false); input.current?.focus(); return; }
    if (jumps[key]) { event.preventDefault(); move(jumps[key]); }
  }

  const monthNames = useMemo(() => Array.from({ length: 12 }, (_, month) => new Intl.DateTimeFormat(locale, { month: "long", timeZone: "UTC" }).format(new Date(Date.UTC(2006, month, 1)))), [locale]);
  const weekdays = useMemo(() => Array.from({ length: 7 }, (_, index) => {
    const date = new Date(Date.UTC(2006, 0, 2 + index)); // 2 Ocak 2006 = Pazartesi
    return { short: new Intl.DateTimeFormat(locale, { weekday: "short", timeZone: "UTC" }).format(date), long: new Intl.DateTimeFormat(locale, { weekday: "long", timeZone: "UTC" }).format(date) };
  }), [locale]);
  const currentYear = new Date().getUTCFullYear();
  const firstYear = Math.max(1950, lower ? Number(lower.slice(0, 4)) : 1950);
  const lastYear = Math.min(currentYear + 10, upper ? Number(upper.slice(0, 4)) : currentYear + 10);
  const years = useMemo(() => {
    const list: number[] = [];
    for (let year = Math.min(firstYear, view.year); year <= Math.max(lastYear, view.year); year++) list.push(year);
    return list;
  }, [firstYear, lastYear, view.year]);

  const offset = (new Date(Date.UTC(view.year, view.month, 1)).getUTCDay() + 6) % 7;
  const days = Array.from({ length: 42 }, (_, index) => toIso(new Date(Date.UTC(view.year, view.month, 1 - offset + index))));
  const shownMonth = (iso: string) => Number(iso.slice(5, 7)) - 1 === view.month;
  const prevMonthTarget = addMonths(`${String(view.year).padStart(4, "0")}-${String(view.month + 1).padStart(2, "0")}-01`, -1);
  const nextMonthTarget = addMonths(`${String(view.year).padStart(4, "0")}-${String(view.month + 1).padStart(2, "0")}-01`, 1);
  const goMonth = (target: string) => { const date = fromIso(target); if (date) setView({ year: date.getUTCFullYear(), month: date.getUTCMonth() }); };
  const prevDisabled = lower !== "" && prevMonthTarget.slice(0, 7) < lower.slice(0, 7);
  const nextDisabled = upper !== "" && nextMonthTarget.slice(0, 7) > upper.slice(0, 7);
  const navButton = "inline-flex size-9 items-center justify-center rounded-control border border-control-border text-text-primary transition-colors hover:bg-surface-elevated disabled:cursor-not-allowed disabled:opacity-40";

  return <div ref={root} className="relative block" onBlur={(event) => { if (open && !root.current?.contains(event.relatedTarget as Node | null)) setOpen(false); }}>
    <div className="relative">
      <input ref={input} id={id} type="text" inputMode="numeric" autoComplete="off" maxLength={10} value={text} placeholder={t("datePickerPlaceholder")}
        aria-invalid={invalid || textInvalid || undefined} aria-describedby={describedBy}
        onChange={(event) => {
          const next = event.target.value;
          setText(next);
          if (next.trim() === "") { setTextInvalid(false); commit(""); return; }
          const parsed = parseText(next, locale);
          if (parsed && !outside(parsed)) { setTextInvalid(false); commit(parsed); } else setTextInvalid(false);
        }}
        onBlur={() => {
          if (text.trim() === "") { setTextInvalid(false); return; }
          const parsed = parseText(text, locale);
          if (parsed && !outside(parsed)) { setText(formatIso(parsed, locale)); setTextInvalid(false); } else setTextInvalid(true);
        }}
        onKeyDown={(event) => { if (event.key === "ArrowDown" && event.altKey) { event.preventDefault(); openCalendar(); } }}
        className={cn(controlClass, "w-full min-w-0 cursor-text pe-11 hover:border-primary/60 focus-visible:border-primary", className)} />
      <button type="button" aria-label={t("datePickerOpen")} aria-haspopup="dialog" aria-expanded={open} aria-controls={open ? dialogId : undefined} onClick={() => (open ? setOpen(false) : openCalendar())}
        className="absolute end-1 top-1/2 inline-flex size-9 -translate-y-1/2 cursor-pointer items-center justify-center rounded-control text-text-secondary transition-colors hover:bg-surface-elevated hover:text-text-primary">
        <Icon name="calendar" width={18} height={18} aria-hidden="true" />
      </button>
    </div>
    {textInvalid && <p role="alert" className="mt-1 text-xs text-brand-text">{t("datePickerInvalid", { example: formatIso("2026-11-22", locale) })}</p>}
    {name && <input type="hidden" name={name} value={iso} />}
    {open && <div id={dialogId} role="dialog" aria-label={t("datePickerDialog")} className="absolute start-0 top-full z-30 mt-2 w-80 max-w-[calc(100vw-2rem)] rounded-panel border border-border bg-surface p-3 shadow-lg">
      <div className="mb-3 flex items-center gap-2">
        <button type="button" aria-label={t("datePickerPrevMonth")} disabled={prevDisabled} onClick={() => goMonth(prevMonthTarget)} className={navButton}><Icon name="chevron" width={16} height={16} className="rotate-90" aria-hidden="true" /></button>
        <div className="min-w-0 flex-1"><GameSelect aria-label={t("datePickerMonth")} value={view.month} onChange={(event) => setView({ ...view, month: Number(event.target.value) })} className={`${controlClass} min-h-9 py-1 ps-3 pe-8 text-sm`}>
          {monthNames.map((label, month) => <option key={month} value={month}>{label}</option>)}
        </GameSelect></div>
        <div className="w-[5.5rem] shrink-0"><GameSelect aria-label={t("datePickerYear")} value={view.year} onChange={(event) => setView({ ...view, year: Number(event.target.value) })} className={`${controlClass} min-h-9 py-1 ps-3 pe-8 text-sm`}>
          {years.map((year) => <option key={year} value={year}>{year}</option>)}
        </GameSelect></div>
        <button type="button" aria-label={t("datePickerNextMonth")} disabled={nextDisabled} onClick={() => goMonth(nextMonthTarget)} className={navButton}><Icon name="chevron" width={16} height={16} className="-rotate-90" aria-hidden="true" /></button>
      </div>
      <div role="grid" aria-label={`${monthNames[view.month]} ${view.year}`} onKeyDown={onGridKey}>
        <div role="row" className="grid grid-cols-7 text-center text-xs text-text-secondary">
          {weekdays.map((day) => <span key={day.long} role="columnheader" aria-label={day.long} className="py-1">{day.short}</span>)}
        </div>
        {Array.from({ length: 6 }, (_, week) => <div key={week} role="row" className="grid grid-cols-7">
          {days.slice(week * 7, week * 7 + 7).map((day) => {
            const selected = day === iso;
            const disabled = outside(day);
            return <button key={day} type="button" role="gridcell" ref={(node) => { if (node) cells.current.set(day, node); else cells.current.delete(day); }}
              tabIndex={day === focused ? 0 : -1} disabled={disabled} aria-selected={selected} aria-current={day === today ? "date" : undefined}
              aria-label={new Intl.DateTimeFormat(locale, { dateStyle: "full", timeZone: "UTC" }).format(fromIso(day) ?? new Date())}
              onClick={() => select(day)}
              className={cn("m-0.5 inline-flex size-9 cursor-pointer items-center justify-center rounded-control text-sm transition-colors hover:bg-surface-elevated disabled:cursor-not-allowed disabled:opacity-30",
                shownMonth(day) ? "text-text-primary" : "text-text-secondary/60",
                day === today && !selected && "border border-primary/60",
                selected && "bg-primary font-semibold text-primary-foreground hover:bg-secondary")}>{Number(day.slice(8, 10))}</button>;
          })}
        </div>)}
      </div>
      <div className="mt-3 flex items-center justify-between text-sm">
        <button type="button" onClick={() => { commit(""); setText(""); setTextInvalid(false); setOpen(false); input.current?.focus(); }} className="cursor-pointer rounded-control px-2 py-1 text-brand-text hover:bg-surface-elevated">{t("datePickerClear")}</button>
        <button type="button" disabled={outside(today)} onClick={() => select(today)} className="cursor-pointer rounded-control px-2 py-1 text-brand-text hover:bg-surface-elevated disabled:cursor-not-allowed disabled:opacity-40">{t("datePickerToday")}</button>
      </div>
    </div>}
  </div>;
}
