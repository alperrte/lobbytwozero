"use client";

import { useId, useRef, type ComponentProps, type KeyboardEvent, type ReactNode } from "react";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { cn } from "@/shared/utils/cn";
import { controlClass } from "./game-status";

export const primaryButtonClass = "inline-flex min-h-11 items-center justify-center gap-2 rounded-control border border-primary bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-colors hover:bg-secondary hover:border-secondary disabled:cursor-not-allowed disabled:opacity-50";
/**
 * Onay kutusu: yerel input korunur (klavye, odak, ekran okuyucu, form davranışı aynı), görünüm özelleştirilir.
 * İşaret CSS ile çizilir; renkler tema belirteçlerindendir.
 */
export const checkboxClass = "grid size-5 shrink-0 cursor-pointer appearance-none place-content-center rounded-md border border-control-border bg-surface transition-colors "
  + "hover:border-primary checked:border-primary checked:bg-primary disabled:cursor-not-allowed disabled:opacity-50 "
  + "before:size-3 before:scale-0 before:bg-primary-foreground before:transition-transform before:[clip-path:polygon(14%_44%,0_65%,50%_100%,100%_16%,80%_0%,43%_62%)] "
  + "checked:before:scale-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary motion-reduce:before:transition-none";
export const panelClass = "rounded-panel border border-border bg-surface p-5";

/**
 * Game sayfalarının kök sınıfı: tıklanabilir her öğede (düğme, sekme, seçim, onay kutusu, özet) tıklanabilir imleç; devre dışı
 * öğede "yasak" imleci. Yalnız Game ekranları için geçerlidir; ortak stillere dokunmaz.
 */
export const gameScopeClass = "[&_button:enabled]:cursor-pointer [&_button:disabled]:cursor-not-allowed [&_select:enabled]:cursor-pointer "
  + "[&_summary]:cursor-pointer [&_[role=tab]]:cursor-pointer [&_[role=button]]:cursor-pointer [&_input[type=checkbox]]:cursor-pointer "
  + "[&_input[type=radio]]:cursor-pointer [&_input[type=date]]:cursor-pointer [&_label:has(input[type=checkbox]:enabled)]:cursor-pointer "
  + "[&_[aria-disabled=true]]:cursor-not-allowed";

/**
 * Açılır liste: yerel select (klavye ve ekran okuyucu davranışı korunur), özel ok simgesi, tutarlı yükseklik ve odak halkası.
 * Seçenek listesinin kendisini tarayıcı çizer; renkleri tema belirteçlerinden alır.
 */
export function GameSelect({ className, children, ...props }: ComponentProps<"select">) {
  return <span className="relative block">
    <select {...props} className={cn("w-full min-w-0 cursor-pointer appearance-none pe-10 transition-colors hover:border-primary/60 focus-visible:border-primary disabled:cursor-not-allowed [&>option]:bg-surface [&>option]:text-text-primary", className)}>{children}</select>
    <Icon name="chevron" width={16} height={16} aria-hidden="true" className="pointer-events-none absolute end-3 top-1/2 -translate-y-1/2 text-text-secondary" />
  </span>;
}

type BadgeTone = "neutral" | "brand" | "success" | "warning";
const tones: Record<BadgeTone, string> = {
  neutral: "border-border bg-surface-elevated text-text-secondary",
  brand: "border-primary/50 bg-primary/10 text-brand-text",
  // Metin rengi yeşil değil birincil metindir: tonlu zeminde iki temada da WCAG AA kontrastı sağlar; yeşil vurgu kenarlık ve zeminde kalır.
  success: "border-success/50 bg-success/10 text-text-primary",
  warning: "border-warning/50 bg-warning/10 text-warning"
};

export function Badge({ children, tone = "neutral", className }: { children: ReactNode; tone?: BadgeTone; className?: string }) {
  return <span className={cn("inline-flex max-w-full items-center rounded-control border px-2 py-0.5 text-xs font-medium", tones[tone], className)}>{children}</span>;
}

/** Yüklenirken yerleşimi koruyan iskelet; hareket azaltma tercihinde animasyon yoktur. */
export function Skeleton({ className }: { className?: string }) {
  return <div aria-hidden="true" className={cn("rounded-panel bg-surface-elevated motion-safe:animate-pulse", className)} />;
}

export function EmptyState({ title, children, action }: { title: string; children?: ReactNode; action?: ReactNode }) {
  return <div className="rounded-panel border border-dashed border-border px-6 py-12 text-center">
    <p className="font-display text-lg font-semibold">{title}</p>
    {children && <div className="mx-auto mt-2 max-w-prose text-sm text-text-secondary">{children}</div>}
    {action && <div className="mt-5">{action}</div>}
  </div>;
}

export function Pagination({ page, totalElements, size, onChange }: { page: number; totalElements: number; size: number; onChange: (page: number) => void }) {
  const t = useTranslations("Game");
  const pages = Math.max(1, Math.ceil(totalElements / size));
  if (pages <= 1 && page === 0) return null;
  return <nav aria-label={t("pagination")} className="mt-8 flex flex-wrap items-center justify-center gap-3">
    <button type="button" className={controlClass} disabled={page === 0} onClick={() => onChange(page - 1)}>{t("previous")}</button>
    <span aria-current="page" className="text-sm tabular-nums text-text-secondary">{t("pageOf", { page: page + 1, pages })}</span>
    <button type="button" className={controlClass} disabled={page + 1 >= pages || page >= 10000} onClick={() => onChange(page + 1)}>{t("next")}</button>
  </nav>;
}

export type TabItem = { id: string; label: string; badge?: ReactNode };

/**
 * Erişilebilir sekme çubuğu: ok tuşları, Home/End ve roving tabindex. Panel, çağıran tarafından
 * `role="tabpanel"` ve `aria-labelledby={tabId(base, id)}` ile verilir.
 */
export function Tabs({ items, value, onChange, label, base }: { items: readonly TabItem[]; value: string; onChange: (id: string) => void; label: string; base?: string }) {
  const generated = useId();
  const prefix = base ?? generated;
  const refs = useRef<Record<string, HTMLButtonElement | null>>({});
  function move(event: KeyboardEvent, index: number) {
    const last = items.length - 1;
    const next = event.key === "ArrowRight" ? (index + 1) % items.length : event.key === "ArrowLeft" ? (index - 1 + items.length) % items.length : event.key === "Home" ? 0 : event.key === "End" ? last : -1;
    if (next < 0) return;
    event.preventDefault();
    const target = items[next];
    onChange(target.id);
    refs.current[target.id]?.focus();
  }
  return <div role="tablist" aria-label={label} className="-mx-1 flex gap-1 overflow-x-auto px-1 pb-px [scrollbar-width:thin]">
    {items.map((item, index) => {
      const selected = item.id === value;
      return <button key={item.id} ref={(node) => { refs.current[item.id] = node; }} id={tabId(prefix, item.id)} type="button" role="tab" aria-selected={selected} aria-controls={panelId(prefix, item.id)} tabIndex={selected ? 0 : -1}
        onClick={() => onChange(item.id)} onKeyDown={(event) => move(event, index)}
        className={cn("inline-flex min-h-11 shrink-0 items-center gap-2 border-b-2 px-4 py-2 text-sm font-semibold transition-colors", selected ? "border-primary text-brand-text" : "border-transparent text-text-secondary hover:border-control-border hover:text-text-primary")}>
        {item.label}{item.badge}
      </button>;
    })}
  </div>;
}
export const tabId = (base: string, id: string) => `${base}-tab-${id}`;
export const panelId = (base: string, id: string) => `${base}-panel-${id}`;
