"use client";

import { useEffect, useId, useState, type FormEvent } from "react";
import { useTranslations } from "next-intl";
import { ApiHttpError } from "@/shared/lib/api/client";
import { useGameMutation } from "../hooks/use-game-mutation";
import type { FormField } from "../schemas/game-forms";
import type { GameMutation } from "../types/game";
import { GameListInput } from "./game-list-input";
import { controlClass } from "./game-status";
import { GameDatePicker } from "./game-date-picker";
import { checkboxClass, GameSelect, primaryButtonClass } from "./game-ui";

const knownStatuses = ["400", "401", "403", "404", "409", "422", "429", "500", "502", "503", "504"];

export function GameActionForm({ title, fields, initial = {}, build, onSaved, destructive = false, resetOnSuccess = false, submitLabel, disabled = false, description, headingLevel: Heading = "h3" }: {
  title: string; fields: readonly FormField[]; initial?: Record<string, string>; build: (values: Record<string, string>) => GameMutation; onSaved?: (value: unknown) => void; destructive?: boolean; resetOnSuccess?: boolean; submitLabel?: string; disabled?: boolean; description?: string; /** Sayfa başlık hiyerarşisine uyum için form başlığının seviyesi. */ headingLevel?: "h2" | "h3" | "h4";
}) {
  const t = useTranslations("Game");
  const id = useId();
  const mutation = useGameMutation();
  const [confirmed, setConfirmed] = useState(false);
  const [lengths, setLengths] = useState<Record<string, number>>({});
  const [dirty, setDirty] = useState(false);
  const error = mutation.error;
  const status = error instanceof ApiHttpError ? String(error.status) : "network";
  const code = error instanceof ApiHttpError ? error.problem?.code : undefined;
  const known = knownStatuses.includes(status);
  // Kaydedilmemiş taslakla sekmeyi kapatma/yenileme tarayıcı uyarısıyla korunur.
  useEffect(() => {
    if (!dirty) return;
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (disabled || (destructive && !confirmed)) return;
    const form = event.currentTarget;
    const values = Object.fromEntries(new FormData(form).entries()) as Record<string, string>;
    const result = await mutation.run(build(values));
    if (result.ok) { onSaved?.(result.value); setConfirmed(false); setDirty(false); if (resetOnSuccess) { form.reset(); setLengths({}); } }
  }
  return <form onSubmit={submit} onInput={() => setDirty(true)} aria-label={title} aria-busy={mutation.pending || undefined} className="space-y-4 border-t border-border py-6">
    <div><Heading className="font-display text-xl font-semibold">{title}</Heading>{description && <p className="mt-1 max-w-prose text-sm text-text-secondary">{description}</p>}</div>
    <fieldset disabled={mutation.pending || disabled} className="grid min-w-0 gap-4 sm:grid-cols-2">
      {fields.map((field) => {
        const invalid = error instanceof ApiHttpError && error.problem?.fields?.some((name) => name.split(".")[0].split("[")[0] === field.name);
        const fieldId = `${id}-${field.name}`;
        const helpKey = field.helpKey ?? `help.${field.name}`;
        const hasHelp = t.has(helpKey);
        const describedBy = [hasHelp ? `${fieldId}-help` : "", invalid ? `${id}-error` : ""].filter(Boolean).join(" ") || undefined;
        const common = { id: fieldId, name: field.name, required: field.required, minLength: field.minLength, maxLength: field.max, defaultValue: initial[field.name] ?? (field.name === "gameType" ? "MAIN_GAME" : ""), "aria-invalid": invalid || undefined, "aria-describedby": describedBy, className: `${controlClass} w-full min-w-0` };
        const wide = field.kind === "textarea" || field.kind === "list";
        return <div key={field.name} className={`flex min-w-0 flex-col gap-2 text-sm ${wide ? "sm:col-span-2" : ""}`}>
          <label htmlFor={fieldId} className={field.required ? "after:ms-1 after:text-brand-text after:content-['*'_/_'']" : undefined}>{t(`fields.${field.name}`)}</label>
          {field.kind === "list"
            ? <GameListInput id={fieldId} name={field.name} label={t(`fields.${field.name}`)} initial={initial[field.name] ?? ""} max={field.max} required={field.required} describedBy={describedBy} invalid={invalid} />
            : field.options ? <GameSelect {...common}>{field.options.map((value) => <option key={value} value={value}>{value ? t.has(`choices.${value}`) ? t(`choices.${value}`) : value : t("unknown")}</option>)}</GameSelect>
            : field.kind === "textarea" ? <textarea {...common} rows={5} onChange={(event) => setLengths((previous) => ({ ...previous, [field.name]: event.target.value.length }))} />
            : field.kind === "date" ? <GameDatePicker id={fieldId} name={field.name} defaultValue={initial[field.name] ?? ""} min="0001-01-01" max="9999-12-31" invalid={Boolean(invalid)} describedBy={describedBy} onValueChange={() => setDirty(true)} />
            : <input {...common} type={field.kind ?? "text"} pattern={field.pattern} />}
          {hasHelp && <span id={`${fieldId}-help`} className="text-xs text-text-secondary">{t(helpKey)}</span>}
          {field.kind === "textarea" && field.max && <span className="text-xs text-text-secondary">{t("characters", { count: lengths[field.name] ?? initial[field.name]?.length ?? 0, max: field.max })}</span>}
        </div>;
      })}
    </fieldset>
    {destructive && <label className="flex min-h-11 items-center gap-3 text-sm"><input type="checkbox" className={checkboxClass} checked={confirmed} disabled={mutation.pending} onChange={(event) => setConfirmed(event.target.checked)} required />{t("confirmAction")}</label>}
    {Boolean(error) && <div id={`${id}-error`} role="alert" className="space-y-1 text-sm text-text-primary">
      <p>{code === "FOREIGN_REFERENCE_SAFETY_UNVERIFIED" ? t("foreignReferenceBlocked") : code === "INVALID_MEDIA_URL" ? t("invalidMediaUrl") : known ? t(`errors.${status}`) : t("mutationUnknown")}</p>
      {status === "409" && code !== "FOREIGN_REFERENCE_SAFETY_UNVERIFIED" && <p className="text-text-secondary">{t("conflictDraftKept")}</p>}
    </div>}
    {mutation.saved && <p role="status" className="text-sm text-success">{t("saved")}</p>}
    {dirty && !mutation.saved && <p className="text-xs text-text-secondary">{t("unsavedChanges")}</p>}
    <button type="submit" className={destructive ? controlClass : primaryButtonClass} disabled={disabled || mutation.pending || (destructive && !confirmed)}>{mutation.pending ? t("saving") : submitLabel || t("save")}</button>
  </form>;
}
