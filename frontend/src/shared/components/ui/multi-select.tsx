"use client";

import {
  useCallback,
  useEffect,
  useId,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type KeyboardEvent as ReactKeyboardEvent
} from "react";
import { createPortal } from "react-dom";
import { Icon } from "@/shared/components/ui/icons";
import { selectTriggerClass, type SelectOption } from "@/shared/components/ui/select";
import { cn } from "@/shared/utils/cn";

export type MultiSelectProps = {
  options: readonly SelectOption[];
  /** Kontrollü kullanım. Verilmezse bileşen `defaultValue` ile kendi durumunu tutar. */
  value?: readonly string[];
  defaultValue?: readonly string[];
  onChange?: (value: string[]) => void;
  /** Verilirse seçili her değer aynı adlı ayrı bir gizli girdide taşınır; `FormData.getAll(name)` ile okunur. */
  name?: string;
  id?: string;
  /** Hiçbir şey seçili değilken tetikleyicide gösterilir (ör. "Tümü"). */
  placeholder?: string;
  disabled?: boolean;
  size?: "sm" | "md";
  variant?: "default" | "oval";
  className?: string;
  "aria-label"?: string;
  "aria-labelledby"?: string;
  "aria-describedby"?: string;
};

type Placement = { container: Element; left: number; top: number; width: number; maxHeight: number; above: boolean };

const GUTTER = 8;
const MAX_LIST_HEIGHT = 288;
const TYPEAHEAD_RESET_MS = 600;

/**
 * Çoklu seçimli açılır liste. `Select` ile aynı APG "combobox" kalıbını kullanır; fark: seçenekler onay
 * kutusu görünümündedir, seçim yapınca liste açık kalır (`aria-multiselectable`), Esc/dış tıklama/Tab kapatır.
 * Tetikleyici seçimi özetler: tek seçimde etiket, birden çokta "ilk etiket +N".
 */
export function MultiSelect({
  options,
  value,
  defaultValue,
  onChange,
  name,
  id,
  placeholder,
  disabled = false,
  size = "md",
  variant = "default",
  className,
  "aria-label": ariaLabel,
  "aria-labelledby": ariaLabelledBy,
  "aria-describedby": ariaDescribedBy
}: MultiSelectProps) {
  const autoId = useId();
  const triggerId = id ?? `${autoId}-trigger`;
  const listId = `${autoId}-list`;
  const optionId = (index: number) => `${autoId}-opt-${index}`;

  const controlled = value !== undefined;
  const [inner, setInner] = useState<readonly string[]>(defaultValue ?? []);
  const current = controlled ? value : inner;
  const chosen = useMemo(() => new Set(current), [current]);
  // Seçenek sırası korunur; seçenekte olmayan (eski bağlantıdan gelen) değerler sonda gösterilir.
  const selectedLabels = options.filter((option) => chosen.has(option.value)).map((option) => option.label);
  const known = new Set(options.map((option) => option.value));
  const orphan = current.filter((item) => !known.has(item));
  const labels = [...selectedLabels, ...orphan];

  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);
  const [placement, setPlacement] = useState<Placement | null>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const typed = useRef({ text: "", at: 0 });

  const enabled = useMemo(() => options.flatMap((option, index) => (option.disabled ? [] : [index])), [options]);
  const step = useCallback(
    (from: number, direction: 1 | -1) => {
      if (enabled.length === 0) return -1;
      if (direction === 1) return enabled.find((index) => index > from) ?? enabled[0];
      return [...enabled].reverse().find((index) => index < from) ?? enabled[enabled.length - 1];
    },
    [enabled]
  );

  const close = useCallback((restoreFocus: boolean) => {
    setOpen(false);
    setPlacement(null);
    if (restoreFocus) triggerRef.current?.focus();
  }, []);

  const openList = useCallback(
    (at?: number) => {
      if (disabled) return;
      const firstSelected = options.findIndex((option) => chosen.has(option.value) && !option.disabled);
      setActive(at ?? (firstSelected >= 0 ? firstSelected : (enabled[0] ?? -1)));
      setOpen(true);
    },
    [chosen, disabled, enabled, options]
  );

  const toggle = useCallback(
    (index: number) => {
      const option = options[index];
      if (!option || option.disabled) return;
      const next = chosen.has(option.value) ? current.filter((item) => item !== option.value) : [...current, option.value];
      if (!controlled) setInner(next);
      onChange?.(next);
    },
    [chosen, controlled, current, onChange, options]
  );

  const measure = useCallback(() => {
    const trigger = triggerRef.current;
    if (!trigger) return;
    const rect = trigger.getBoundingClientRect();
    const below = window.innerHeight - rect.bottom - GUTTER;
    const above = rect.top - GUTTER;
    const flip = below < 160 && above > below;
    const room = flip ? above : below;
    const width = Math.max(rect.width, 180);
    setPlacement({
      container: trigger.closest("dialog") ?? document.body,
      left: Math.min(Math.max(GUTTER, rect.left), Math.max(GUTTER, window.innerWidth - width - GUTTER)),
      width,
      maxHeight: Math.max(96, Math.min(MAX_LIST_HEIGHT, room - 4)),
      above: flip,
      top: flip ? rect.top - 4 : rect.bottom + 4
    });
  }, []);

  useLayoutEffect(() => {
    if (open) measure();
  }, [open, measure]);

  useEffect(() => {
    if (!open) return;
    function onPointerDown(event: PointerEvent) {
      const target = event.target as Node;
      if (triggerRef.current?.contains(target) || listRef.current?.contains(target)) return;
      close(false);
    }
    document.addEventListener("pointerdown", onPointerDown);
    window.addEventListener("resize", measure);
    window.addEventListener("scroll", measure, true);
    return () => {
      document.removeEventListener("pointerdown", onPointerDown);
      window.removeEventListener("resize", measure);
      window.removeEventListener("scroll", measure, true);
    };
  }, [open, close, measure]);

  useEffect(() => {
    if (open && active >= 0) document.getElementById(optionId(active))?.scrollIntoView?.({ block: "nearest" });
    // optionId yalnız autoId'ye bağlıdır.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, active, autoId]);

  function typeahead(key: string) {
    const now = Date.now();
    const state = typed.current;
    state.text = now - state.at > TYPEAHEAD_RESET_MS ? key : state.text + key;
    state.at = now;
    const needle = state.text.toLocaleLowerCase();
    const pivot = needle.split("").every((char) => char === needle[0]) ? active : active - 1;
    const ordered = [...enabled.filter((index) => index > pivot), ...enabled.filter((index) => index <= pivot)];
    const hit = ordered.find((index) => options[index].label.toLocaleLowerCase().startsWith(needle));
    if (hit === undefined) return;
    if (open) setActive(hit);
    else openList(hit);
  }

  function onKeyDown(event: ReactKeyboardEvent<HTMLButtonElement>) {
    if (disabled || event.altKey || event.ctrlKey || event.metaKey) return;
    const key = event.key;
    if (!open) {
      if (["ArrowDown", "ArrowUp", "Enter", " "].includes(key)) {
        event.preventDefault();
        openList();
      } else if (key === "Home" || key === "End") {
        event.preventDefault();
        openList(key === "Home" ? enabled[0] : enabled[enabled.length - 1]);
      } else if (key.length === 1) {
        event.preventDefault();
        typeahead(key);
      }
      return;
    }
    if (key === "ArrowDown" || key === "ArrowUp") {
      event.preventDefault();
      setActive((from) => step(from, key === "ArrowDown" ? 1 : -1));
    } else if (key === "Home" || key === "End") {
      event.preventDefault();
      setActive(key === "Home" ? enabled[0] : enabled[enabled.length - 1]);
    } else if (key === "Enter" || key === " ") {
      event.preventDefault();
      if (active >= 0) toggle(active);
    } else if (key === "Escape") {
      event.preventDefault();
      event.nativeEvent.stopPropagation();
      close(true);
    } else if (key === "Tab") {
      close(false);
    } else if (key.length === 1) {
      event.preventDefault();
      typeahead(key);
    }
  }

  const summary = labels.length === 0 ? (placeholder ?? "") : labels[0];
  const extra = labels.length - 1;
  const oval = variant === "oval";

  return (
    <>
      <button
        ref={triggerRef}
        id={triggerId}
        type="button"
        role="combobox"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={open ? listId : undefined}
        aria-activedescendant={open && active >= 0 ? optionId(active) : undefined}
        aria-label={ariaLabel}
        aria-labelledby={ariaLabelledBy}
        aria-describedby={ariaDescribedBy}
        disabled={disabled}
        onClick={() => (open ? close(false) : openList())}
        onKeyDown={onKeyDown}
        onBlur={() => {
          if (open) close(false);
        }}
        className={cn(
          "flex w-full min-w-0 items-center justify-between gap-2 text-start text-text-primary transition-colors duration-150 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus disabled:cursor-not-allowed disabled:opacity-60",
          selectTriggerClass(size, variant),
          className
        )}
      >
        <span className={cn("min-w-0 flex-1 truncate", labels.length === 0 && "text-text-secondary")}>{summary}</span>
        {extra > 0 ? (
          <span className="shrink-0 rounded-full bg-primary px-1.5 text-[11px] font-semibold leading-5 text-primary-foreground" aria-label={`+${extra}`}>
            +{extra}
          </span>
        ) : null}
        <Icon name="chevron" className={cn("size-4 shrink-0 text-text-secondary transition-transform duration-150", open && "rotate-180")} />
      </button>
      {name
        ? current.map((item) => <input key={item} type="hidden" name={name} value={item} disabled={disabled} />)
        : null}
      {open && placement
        ? createPortal(
            <div
              ref={listRef}
              id={listId}
              role="listbox"
              aria-multiselectable="true"
              aria-labelledby={ariaLabelledBy}
              aria-label={ariaLabelledBy ? undefined : ariaLabel}
              onMouseDown={(event) => event.preventDefault()}
              style={{
                position: "fixed",
                left: placement.left,
                top: placement.above ? undefined : placement.top,
                bottom: placement.above ? window.innerHeight - placement.top : undefined,
                width: placement.width,
                maxHeight: placement.maxHeight
              }}
              className={cn(
                "z-[100] overflow-y-auto overscroll-contain border border-border bg-surface p-1 text-text-primary shadow-popover animate-pop-in",
                oval ? "rounded-xl" : "rounded-panel"
              )}
            >
              {options.map((option, index) => {
                const checked = chosen.has(option.value);
                return (
                  <div
                    key={option.value}
                    id={optionId(index)}
                    role="option"
                    aria-selected={checked}
                    aria-disabled={option.disabled || undefined}
                    data-active={index === active || undefined}
                    onPointerMove={() => !option.disabled && active !== index && setActive(index)}
                    onClick={() => toggle(index)}
                    className={cn(
                      "flex min-h-9 cursor-pointer items-center gap-2.5 px-2.5 py-1.5 text-sm transition-colors duration-100 data-[active]:bg-surface-elevated aria-selected:font-semibold aria-selected:text-brand-text aria-disabled:cursor-not-allowed aria-disabled:opacity-50",
                      oval ? "rounded-lg" : "rounded-control"
                    )}
                  >
                    <span
                      aria-hidden="true"
                      className={cn(
                        "grid size-4 shrink-0 place-items-center rounded-[5px] border transition-colors duration-100",
                        checked ? "border-primary bg-primary text-primary-foreground" : "border-control-border bg-transparent"
                      )}
                    >
                      {checked ? <Icon name="check" className="size-3" /> : null}
                    </span>
                    <span className="min-w-0 flex-1 truncate">{option.label}</span>
                  </div>
                );
              })}
            </div>,
            placement.container
          )
        : null}
    </>
  );
}
