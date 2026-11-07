"use client";

import { useId, useState, type FormEvent } from "react";
import { useTranslations } from "next-intl";
import { ApiHttpError } from "@/shared/lib/api/client";
import { useGameMutation } from "../hooks/use-game-mutation";
import type { FormField } from "../schemas/game-forms";
import type { GameMutation } from "../types/game";
import { controlClass } from "./game-status";

export function GameActionForm({ title, fields, initial = {}, build, onSaved, destructive = false, resetOnSuccess = false, submitLabel, disabled = false }: {
  title: string; fields: readonly FormField[]; initial?: Record<string, string>; build: (values: Record<string, string>) => GameMutation; onSaved?: (value: unknown) => void; destructive?: boolean; resetOnSuccess?: boolean; submitLabel?: string; disabled?: boolean;
}) {
  const t = useTranslations("Game");
  const id = useId();
  const mutation = useGameMutation();
  const [confirmed, setConfirmed] = useState(false);
  const [lengths, setLengths] = useState<Record<string, number>>({});
  const error = mutation.error;
  const status = error instanceof ApiHttpError ? String(error.status) : "network";
  const known = ["400", "401", "403", "404", "409", "422", "429", "500", "502", "503", "504"].includes(status);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (disabled || (destructive && !confirmed)) return;
    const form = event.currentTarget;
    const values = Object.fromEntries(new FormData(form).entries()) as Record<string, string>;
    const result = await mutation.run(build(values));
    if (result.ok) { onSaved?.(result.value); setConfirmed(false); if (resetOnSuccess) { form.reset(); setLengths({}); } }
  }
  return <form onSubmit={submit} aria-label={title} className="space-y-4 border-t border-border py-6">
    <h3 className="font-display text-xl font-semibold">{title}</h3>
    <fieldset disabled={mutation.pending || disabled} className="grid min-w-0 gap-4 sm:grid-cols-2">
      {fields.map((field) => {
        const invalid = error instanceof ApiHttpError && error.problem?.fields?.some((name) => name.split(".")[0].split("[")[0] === field.name);
        const common = { id: `${id}-${field.name}`, name: field.name, required: field.required, minLength: field.minLength, maxLength: field.max, defaultValue: initial[field.name] ?? (field.name === "gameType" ? "MAIN_GAME" : ""), "aria-invalid": invalid || undefined, "aria-describedby": invalid ? `${id}-error` : undefined, className: `${controlClass} w-full min-w-0` };
        return <div key={field.name} className={`flex min-w-0 flex-col gap-2 text-sm ${field.kind === "textarea" ? "sm:col-span-2" : ""}`}>
          <label htmlFor={common.id}>{t(`fields.${field.name}`)}</label>
          {field.options ? <select {...common}>{field.options.map((value) => <option key={value} value={value}>{value ? t.has(`choices.${value}`) ? t(`choices.${value}`) : value : t("unknown")}</option>)}</select> : field.kind === "textarea" ? <textarea {...common} rows={5} onChange={(event) => setLengths((previous) => ({ ...previous, [field.name]: event.target.value.length }))} /> : <input {...common} type={field.kind ?? "text"} pattern={field.pattern} />}
          {field.kind === "textarea" && field.max && <span className="text-xs text-text-secondary">{t("characters", { count: lengths[field.name] ?? initial[field.name]?.length ?? 0, max: field.max })}</span>}
        </div>;
      })}
    </fieldset>
    {destructive && <label className="flex min-h-11 items-center gap-3 text-sm"><input type="checkbox" checked={confirmed} disabled={mutation.pending} onChange={(event) => setConfirmed(event.target.checked)} required />{t("confirmAction")}</label>}
    {Boolean(error) && <p id={`${id}-error`} role="alert" className="text-sm text-text-primary">{known ? t(`errors.${status}`) : t("mutationUnknown")}</p>}
    {mutation.saved && <p role="status" className="text-sm text-success">{t("saved")}</p>}
    <button type="submit" className={controlClass} disabled={disabled || mutation.pending || (destructive && !confirmed)}>{mutation.pending ? t("saving") : submitLabel || t("save")}</button>
  </form>;
}
