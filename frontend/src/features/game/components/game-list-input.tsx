"use client";

import { useEffect, useRef, useState, type KeyboardEvent } from "react";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { controlClass } from "./game-status";

const separator = /[,;\n]/;
export const splitList = (value: string): string[] => [...new Set(value.split(",").map((item) => item.trim()).filter(Boolean))];

/**
 * Çoklu değer girişi (geliştirici, tür, etiket vb.). Değerler çip olarak görünür; forma tek bir gizli alan
 * olarak virgülle ayrılmış metin yazılır, böylece mevcut form ve değişiklik algılama mantığı aynı kalır.
 */
export function GameListInput({ id, name, label, initial = "", max, required, describedBy, invalid }: { id: string; name: string; label: string; initial?: string; max?: number; required?: boolean; describedBy?: string; invalid?: boolean }) {
  const t = useTranslations("Game");
  const [items, setItems] = useState(() => splitList(initial));
  const [draft, setDraft] = useState("");
  const hidden = useRef<HTMLInputElement>(null);
  const joined = items.join(", ");
  const full = max !== undefined && joined.length >= max;
  useEffect(() => {
    const form = hidden.current?.form;
    if (!form) return;
    const reset = () => { setItems(splitList(initial)); setDraft(""); };
    form.addEventListener("reset", reset);
    return () => form.removeEventListener("reset", reset);
  }, [initial]);
  function notify() { queueMicrotask(() => hidden.current?.dispatchEvent(new Event("input", { bubbles: true }))); }
  function commit(raw: string) {
    const additions = raw.split(separator).map((item) => item.trim()).filter(Boolean);
    if (!additions.length) { setDraft(""); return; }
    const next = [...new Set([...items, ...additions])];
    if (max !== undefined && next.join(", ").length > max) return;
    setItems(next); setDraft(""); notify();
  }
  function remove(value: string) { setItems(items.filter((item) => item !== value)); notify(); }
  function key(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === "Enter" || event.key === ",") { if (draft.trim() || event.key === ",") { event.preventDefault(); commit(draft); } }
    else if (event.key === "Backspace" && !draft && items.length) remove(items[items.length - 1]);
  }
  return <div className="min-w-0">
    <input ref={hidden} type="hidden" name={name} value={joined} />
    {items.length > 0 && <ul aria-label={label} className="mb-2 flex flex-wrap gap-2">{items.map((value) => <li key={value}>
      <button type="button" onClick={() => remove(value)} aria-label={t("removeValue", { value })} className="inline-flex min-h-9 max-w-full items-center gap-2 rounded-full border border-control-border bg-surface-elevated px-3 text-sm hover:border-primary">
        <span className="[overflow-wrap:anywhere]">{value}</span><Icon name="close" width={14} height={14} /></button></li>)}</ul>}
    <div className="flex gap-2">
      <input id={id} type="text" value={draft} required={required && !items.length} disabled={full && !draft} onChange={(event) => setDraft(event.target.value)} onKeyDown={key} onBlur={() => draft.trim() && commit(draft)} aria-invalid={invalid || undefined} aria-describedby={describedBy} autoComplete="off" className={`${controlClass} w-full min-w-0`} />
      <button type="button" className={controlClass} disabled={!draft.trim() || full} onClick={() => commit(draft)}>{t("addValue")}</button>
    </div>
  </div>;
}
