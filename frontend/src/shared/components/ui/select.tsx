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
import { cn } from "@/shared/utils/cn";

export type SelectOption = {
  value: string;
  label: string;
  disabled?: boolean;
};

export type SelectProps = {
  options: readonly SelectOption[];
  /** Kontrollü kullanım. Verilmezse bileşen `defaultValue` ile kendi durumunu tutar. */
  value?: string;
  defaultValue?: string;
  onChange?: (value: string) => void;
  /** Verilirse seçili değer gizli bir girdide taşınır; `<form>` / `FormData` ile çalışır. */
  name?: string;
  /** `<label htmlFor>` ile eşleşmesi için tetikleyiciye uygulanır. */
  id?: string;
  /** Seçili değer seçeneklerde yoksa tetikleyicide gösterilir. */
  placeholder?: string;
  disabled?: boolean;
  required?: boolean;
  size?: "sm" | "md";
  /** `oval`: 12px yuvarlak, zemini yüzeyden bir ton açık, kenarlığı hover/odakta görünen modern görünüm. */
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

const sizeClass = { sm: "h-[35px] text-xs", md: "h-10 text-sm" } as const;

/** Tetikleyici sınıfları; `MultiSelect` ile ortak tutulur ki iki bileşen aynı görünsün. */
export function selectTriggerClass(size: "sm" | "md", variant: "default" | "oval"): string {
  return variant === "oval"
    ? cn("h-10 rounded-xl border border-transparent bg-[var(--ltz-select-bg,var(--ltz-surface-elevated))] px-3.5 text-sm hover:border-control-border aria-expanded:border-primary")
    : cn("rounded-control border border-control-border bg-[var(--ltz-select-bg,var(--ltz-surface))] px-3 hover:border-text-secondary aria-expanded:border-primary", sizeClass[size]);
}

/**
 * LTZ'ye özgü açılır liste (tarayıcının yerel `<select>` görünümü yerine). APG "select-only combobox"
 * kalıbıdır: tetikleyici `role="combobox"` düğmesidir, odak hep tetikleyicide kalır ve etkin seçenek
 * `aria-activedescendant` ile duyurulur.
 *
 * Klavye: ↑/↓/Home/End gezinir, Enter/Boşluk seçer, Esc kapatır (odak tetikleyicide kalır; üstteki
 * diyalog Esc'yi görmez), Tab kapatır, harf yazarak seçeneğe atlanır.
 *
 * Liste `position: fixed` ile tetikleyiciye göre konumlanır, yer yoksa yukarı açılır; atası kırpsa bile
 * görünür. Açık bir modal `<dialog>` içindeyse listeyi o dialog içine, değilse `body`'ye bağlar.
 *
 * Görünüm: tetikleyici zemini `--ltz-select-bg` özel özelliğiyle özelleştirilir (varsayılan yüzey rengi).
 * Yalnız semantik tokenlar kullanılır; açık/koyu tema kendiliğinden uyar.
 */
export function Select({
  options,
  value,
  defaultValue,
  onChange,
  name,
  id,
  placeholder,
  disabled = false,
  required = false,
  size = "md",
  variant = "default",
  className,
  "aria-label": ariaLabel,
  "aria-labelledby": ariaLabelledBy,
  "aria-describedby": ariaDescribedBy
}: SelectProps) {
  const autoId = useId();
  const triggerId = id ?? `${autoId}-trigger`;
  const listId = `${autoId}-list`;
  const optionId = (index: number) => `${autoId}-opt-${index}`;

  const controlled = value !== undefined;
  const [inner, setInner] = useState(defaultValue ?? "");
  const current = controlled ? value : inner;
  const selectedIndex = options.findIndex((option) => option.value === current);
  const selected = selectedIndex >= 0 ? options[selectedIndex] : undefined;

  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);
  const [placement, setPlacement] = useState<Placement | null>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const typed = useRef({ text: "", at: 0 });

  const enabled = useMemo(
    () => options.flatMap((option, index) => (option.disabled ? [] : [index])),
    [options]
  );

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
      setActive(at ?? (selectedIndex >= 0 && !options[selectedIndex].disabled ? selectedIndex : (enabled[0] ?? -1)));
      setOpen(true);
    },
    [disabled, enabled, options, selectedIndex]
  );

  const choose = useCallback(
    (index: number) => {
      const option = options[index];
      if (!option || option.disabled) return;
      if (!controlled) setInner(option.value);
      if (option.value !== current) onChange?.(option.value);
      close(true);
    },
    [close, controlled, current, onChange, options]
  );

  const measure = useCallback(() => {
    const trigger = triggerRef.current;
    if (!trigger) return;
    const rect = trigger.getBoundingClientRect();
    const below = window.innerHeight - rect.bottom - GUTTER;
    const above = rect.top - GUTTER;
    const flip = below < 160 && above > below;
    const room = flip ? above : below;
    const maxHeight = Math.max(96, Math.min(MAX_LIST_HEIGHT, room - 4));
    const width = Math.max(rect.width, 160);
    const left = Math.min(Math.max(GUTTER, rect.left), Math.max(GUTTER, window.innerWidth - width - GUTTER));
    setPlacement({
      // Modal dialog üst katmanda olduğundan liste onun içine bağlanır.
      container: trigger.closest("dialog") ?? document.body,
      left,
      width,
      maxHeight,
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
    if (open && active >= 0) {
      document.getElementById(optionId(active))?.scrollIntoView?.({ block: "nearest" });
    }
    // optionId yalnız autoId'ye bağlıdır.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, active, autoId]);

  function typeahead(key: string) {
    const now = Date.now();
    const state = typed.current;
    state.text = now - state.at > TYPEAHEAD_RESET_MS ? key : state.text + key;
    state.at = now;
    const needle = state.text.toLocaleLowerCase();
    // Aynı harf tekrarlanıyorsa sıradaki eşleşmeye geçilir; çok harfli önek etkin satırdan başlayarak aranır.
    const repeated = needle.split("").every((char) => char === needle[0]);
    const pivot = repeated ? active : active - 1;
    const ordered = [...enabled.filter((index) => index > pivot), ...enabled.filter((index) => index <= pivot)];
    const hit = ordered.find((index) => options[index].label.toLocaleLowerCase().startsWith(needle)) ??
      (repeated && needle.length > 1 ? ordered.find((index) => options[index].label.toLocaleLowerCase().startsWith(needle[0])) : undefined);
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
      if (active >= 0) choose(active);
      else close(true);
    } else if (key === "Escape") {
      // Üstteki diyalog yalnız seçenek listesi kapalıyken Esc ile kapanır.
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
        aria-required={required || undefined}
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
        <span className={cn("min-w-0 flex-1 truncate", !selected && "text-text-secondary")}>
          {selected ? selected.label : (placeholder ?? "")}
        </span>
        <Icon
          name="chevron"
          className={cn("size-4 shrink-0 text-text-secondary transition-transform duration-150", open && "rotate-180")}
        />
      </button>
      {name ? <input type="hidden" name={name} value={current} disabled={disabled} /> : null}
      {open && placement
        ? createPortal(
            <div
              ref={listRef}
              id={listId}
              role="listbox"
              aria-labelledby={ariaLabelledBy}
              aria-label={ariaLabelledBy ? undefined : ariaLabel}
              // Seçenek ve kaydırma çubuğuna basmak tetikleyicinin odağını almasın.
              onMouseDown={(event) => event.preventDefault()}
              style={{
                position: "fixed",
                left: placement.left,
                top: placement.above ? undefined : placement.top,
                bottom: placement.above ? window.innerHeight - placement.top : undefined,
                width: placement.width,
                maxHeight: placement.maxHeight
              }}
              className={cn("z-[100] overflow-y-auto overscroll-contain border border-border bg-surface p-1 text-text-primary shadow-popover animate-pop-in", variant === "oval" ? "rounded-xl" : "rounded-panel")}
            >
              {options.map((option, index) => (
                <div
                  key={option.value}
                  id={optionId(index)}
                  role="option"
                  aria-selected={index === selectedIndex}
                  aria-disabled={option.disabled || undefined}
                  data-active={index === active || undefined}
                  onPointerMove={() => !option.disabled && active !== index && setActive(index)}
                  onClick={() => choose(index)}
                  className={cn("flex min-h-9 cursor-pointer items-center justify-between gap-3 px-2.5 py-1.5 text-sm transition-colors duration-100 data-[active]:bg-surface-elevated aria-selected:font-semibold aria-selected:text-brand-text aria-selected:shadow-[inset_2px_0_0_var(--ltz-primary)] aria-disabled:cursor-not-allowed aria-disabled:opacity-50", variant === "oval" ? "rounded-lg" : "rounded-control")}
                >
                  <span className="min-w-0 flex-1 truncate">{option.label}</span>
                  {index === selectedIndex ? <Icon name="check" className="size-4 shrink-0 text-primary" /> : null}
                </div>
              ))}
            </div>,
            placement.container
          )
        : null}
    </>
  );
}
